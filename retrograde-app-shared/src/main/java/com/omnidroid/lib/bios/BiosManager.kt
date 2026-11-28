package com.omnidroid.lib.bios

import com.omnidroid.common.files.safeDelete
import com.omnidroid.common.kotlin.associateByNotNull
import com.omnidroid.common.kotlin.writeToFile
import com.omnidroid.lib.core.assetsmanager.ManagedFirmwareAssetsManager
import com.omnidroid.lib.core.assetsmanager.SegaCdAssetsManager
import com.omnidroid.lib.library.SystemCoreConfig
import com.omnidroid.lib.library.SystemID
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.storage.DirectoriesManager
import com.omnidroid.lib.storage.StorageFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.zip.CRC32

class BiosManager(
    private val directoriesManager: DirectoriesManager,
) {
    private val crcLookup = SUPPORTED_BIOS.associateByNotNull { it.externalCRC32 }
    private val nameLookup =
        SUPPORTED_BIOS
            .flatMap { bios ->
                listOfNotNull(bios.libretroFileName.substringAfterLast('/'), bios.externalName).map {
                    it.lowercase(java.util.Locale.US) to
                        bios
                }
            }.toMap()

    fun getMissingBiosFiles(
        coreConfig: SystemCoreConfig,
        game: Game,
    ): List<String> {
        val regionalBiosFiles = coreConfig.regionalBIOSFiles
        val systemDir = directoriesManager.getSystemDirectory()

        fun present(biosName: String): Boolean =
            File(systemDir, biosName).exists() ||
                File(systemDir, "pcsx2/bios/$biosName").exists()

        val gameLabels = regionLabels(game.title)
        val matchedRegions = gameLabels.intersect(regionalBiosFiles.keys)
        Timber.d("Found game labels: $gameLabels matched=$matchedRegions")

        // Prefer the BIOS for the title's region(s). When the region is unknown, only the
        // core's default requiredBIOSFiles apply — never "every regional BIOS".
        val required =
            if (matchedRegions.isNotEmpty()) {
                matchedRegions.mapNotNull { regionalBiosFiles[it] }.distinct()
            } else {
                coreConfig.requiredBIOSFiles
            }

        // Multi-region titles (e.g. "USA, Europe") only need one of the matched BIOS files.
        if (matchedRegions.size > 1 && required.any(::present)) {
            return emptyList()
        }

        // If the preferred regional BIOS is missing, but another valid BIOS for this core
        // is present (e.g. user has scph39001.bin for a European game), allow it as a fallback
        // so cross-region games can still boot.
        if (regionalBiosFiles.isNotEmpty() && required.none(::present)) {
            val allCoreBios = (coreConfig.requiredBIOSFiles + regionalBiosFiles.values).distinct()
            if (allCoreBios.any(::present)) {
                return emptyList()
            }
        }

        return required.filterNot(::present)
    }

    private fun regionLabels(title: String): Set<String> =
        Regex("""\(([^)]+)\)""")
            .findAll(title)
            .flatMap { match -> match.groupValues[1].split(',', '/', '+') }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

    fun deleteBiosBefore(timestampMs: Long) {
        Timber.i("Pruning old bios files")
        val systemDir = directoriesManager.getSystemDirectory()
        // Only prune scan-imported copies in the system root. Asset-managed copies under
        // pcsx2/bios/ must survive rescans or PS2 loses its downloaded BIOS every index.
        SUPPORTED_BIOS
            .map { File(systemDir, it.libretroFileName) }
            .filter { it.exists() && it.lastModified() < normalizeTimestamp(timestampMs) }
            .filterNot {
                SegaCdAssetsManager.isPublishedBios(systemDir, it) ||
                    ManagedFirmwareAssetsManager.isPublishedBios(systemDir, it)
            }.forEach {
                Timber.d("Pruning old bios file: ${it.path}")
                it.safeDelete()
            }
    }

    private fun isBiosPresent(
        systemDir: File,
        bios: Bios,
    ): Boolean {
        if (File(systemDir, bios.libretroFileName).exists() ||
            File(systemDir, "pcsx2/bios/${bios.libretroFileName}").exists()
        ) {
            return true
        }
        if (bios.externalName != null &&
            (File(systemDir, bios.externalName).exists() || File(systemDir, "pcsx2/bios/${bios.externalName}").exists())
        ) {
            return true
        }
        if (bios.systemID == SystemID.PS2) {
            return File(ps2Library(systemDir), bios.libretroFileName).isFile
        }
        return false
    }

    @Deprecated("Use the suspend variant")
    fun getBiosInfo(): BiosInfo {
        refreshPs2Library()
        val systemDir = directoriesManager.getSystemDirectory()
        val bios =
            SUPPORTED_BIOS
                .groupBy {
                    isBiosPresent(systemDir, it)
                }.withDefault { listOf() }

        return BiosInfo(bios.getValue(true), bios.getValue(false), ps2Selection())
    }

    suspend fun getBiosInfoAsync(): BiosInfo =
        withContext(Dispatchers.IO) {
            getBiosInfo()
        }

    fun tryAddBiosAfter(
        storageFile: StorageFile,
        inputStream: InputStream,
        timestampMs: Long,
    ): Boolean {
        val bios = findByCRC(storageFile) ?: findByName(storageFile) ?: return false

        Timber.i("Importing bios file: $bios")

        val biosFile = File(directoriesManager.getSystemDirectory(), bios.libretroFileName)
        biosFile.parentFile?.mkdirs()
        if (biosFile.exists() && biosFile.setLastModified(normalizeTimestamp(timestampMs))) {
            Timber.d("Bios file already present. Updated last modification date.")
        } else {
            Timber.d("Bios file not available. Copying new file.")
            inputStream.writeToFile(biosFile)
        }

        if (bios.systemID == SystemID.PS2) {
            try {
                adoptPs2File(directoriesManager.getSystemDirectory(), biosFile)
            } catch (e: Exception) {
                Timber.e(e, "Error storing PS2 bios")
            }
        }
        return true
    }

    fun stageActivePs2Bios(game: Game) {
        val available = refreshPs2Library()
        if (available.isEmpty()) return
        val names = available.map { it.libretroFileName }.toSet()
        val chosen = Ps2Bios.choose(regionLabels(game.title), names, ps2Selection()) ?: return
        val systemDir = directoriesManager.getSystemDirectory()
        val source = File(ps2Library(systemDir), chosen)
        val live = File(systemDir, "pcsx2/bios")
        live.mkdirs()
        live
            .listFiles()
            ?.filter {
                it.isFile &&
                    it.name.endsWith(".bin", ignoreCase = true) &&
                    !it.name.equals(chosen, ignoreCase = true)
            }?.forEach { it.safeDelete() }
        val destination = File(live, chosen)
        if (!destination.exists() || destination.length() != source.length()) {
            source.copyTo(destination, overwrite = true)
        }
    }

    fun ps2Selection(): String? {
        val text =
            File(ps2Library(directoriesManager.getSystemDirectory()), SELECTION_FILE)
                .takeIf {
                    it.isFile
                }?.readText()
                ?.trim()
        if (text.isNullOrBlank() || text == Ps2Bios.AUTO) return null
        return text
    }

    fun setPs2Selection(fileName: String?) {
        val library = ps2Library(directoriesManager.getSystemDirectory())
        library.mkdirs()
        File(library, SELECTION_FILE).writeText(fileName ?: Ps2Bios.AUTO)
    }

    private fun refreshPs2Library(): List<Bios> = refreshPs2Library(directoriesManager.getSystemDirectory())

    private fun findByCRC(storageFile: StorageFile): Bios? = crcLookup[storageFile.crc]

    private fun findByName(storageFile: StorageFile): Bios? =
        nameLookup[storageFile.name.lowercase(java.util.Locale.US)]

    fun matchesBiosCrc(crc: String?): Boolean = crc != null && crcLookup.containsKey(crc)

    private fun normalizeTimestamp(timestamp: Long) = (timestamp / 1000) * 1000

    data class BiosInfo(
        val detected: List<Bios>,
        val notDetected: List<Bios>,
        val ps2Selection: String? = null,
    )

    companion object {
        const val MAX_BIOS_CANDIDATE_BYTES = 4L * 1024 * 1024
        private const val PS2_LIBRARY = "ps2-bios"
        private const val SELECTION_FILE = "selection"

        fun ps2Library(systemDir: File): File = File(systemDir, PS2_LIBRARY)

        fun refreshPs2Library(systemDir: File): List<Bios> {
            val library = ps2Library(systemDir)
            library.mkdirs()
            val known = SUPPORTED_BIOS.filter { it.systemID == SystemID.PS2 }
            biosCandidates(systemDir).forEach { file ->
                val match = ps2ByCrc(crc32Hex(file)) ?: return@forEach
                val dest = File(library, match.libretroFileName)
                if (file.absolutePath != dest.absolutePath && !dest.exists()) {
                    file.copyTo(dest)
                }
            }
            return known.filter { bios ->
                val file = File(library, bios.libretroFileName)
                file.isFile && ps2ByCrc(crc32Hex(file))?.libretroFileName == bios.libretroFileName
            }
        }

        fun adoptPs2File(
            systemDir: File,
            source: File,
        ): Boolean {
            val match = ps2ByCrc(crc32Hex(source)) ?: return false
            val dest = File(ps2Library(systemDir), match.libretroFileName)
            dest.parentFile?.mkdirs()
            if (!dest.exists() || ps2ByCrc(crc32Hex(dest))?.libretroFileName != match.libretroFileName) {
                source.copyTo(dest, overwrite = true)
            }
            return true
        }

        private fun biosCandidates(systemDir: File): List<File> {
            val found = ArrayList<File>()

            fun collect(dir: File) {
                dir
                    .listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".bin", ignoreCase = true) }
                    ?.let { found += it }
            }
            collect(systemDir)
            collect(File(systemDir, "pcsx2/bios"))
            collect(ps2Library(systemDir))
            return found
        }

        private fun ps2ByCrc(crc: String): Bios? {
            SUPPORTED_BIOS
                .find { it.systemID == SystemID.PS2 && it.externalCRC32.equals(crc, ignoreCase = true) }
                ?.let { return it }
            val fileName = Ps2Bios.fileNameForCrc(crc) ?: return null
            return SUPPORTED_BIOS.find { it.libretroFileName == fileName }
        }

        private fun crc32Hex(file: File): String {
            val crc = CRC32()
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    crc.update(buffer, 0, read)
                }
            }
            return "%08X".format(crc.value)
        }

        fun isKnownBiosName(name: String): Boolean =
            SUPPORTED_BIOS.any { bios ->
                bios.libretroFileName.equals(name, ignoreCase = true) ||
                    bios.externalName?.equals(name, ignoreCase = true) == true
            }

        private val SUPPORTED_BIOS =
            listOf(
                Bios(
                    "5200.rom",
                    "281F20EA4320404EC820FB7EC0693B38",
                    "5200 BIOS - Optional",
                    SystemID.ATARI5200,
                    null,
                    "5200.rom",
                ),
                Bios(
                    "ATARIXL.ROM",
                    "06DAAC977823773A3EEA3422FD26A703",
                    "Atari XL/XE OS BIOS - Optional",
                    SystemID.ATARI800,
                    null,
                    "ATARIXL.ROM",
                ),
                Bios(
                    "ATARIBAS.ROM",
                    "0BAC0C6A50104045D902DF4503A4C30B",
                    "BASIC interpreter BIOS - Optional",
                    SystemID.ATARI800,
                    null,
                    "ATARIBAS.ROM",
                ),
                Bios(
                    "ATARIOSA.ROM",
                    "EB1F32F5D9F382DB1BBFB8D7F9CB343A",
                    "Atari 400/800 PAL BIOS - Optional",
                    SystemID.ATARI800,
                    null,
                    "ATARIOSA.ROM",
                ),
                Bios(
                    "ATARIOSB.ROM",
                    "A3E8D617C95D08031FE1B20D541434B2",
                    "Atari 400/800 NTSC BIOS - Optional",
                    SystemID.ATARI800,
                    null,
                    "ATARIOSB.ROM",
                ),
                Bios(
                    "saturn_bios.bin",
                    "AF5828FDFF51384F99B3C4926BE27762",
                    "Saturn BIOS - Optional",
                    SystemID.SATURN,
                    null,
                    "saturn_bios.bin",
                ),
                Bios(
                    "panafz1.bin",
                    "F47264DD47FE30F73AB3C010015C155B",
                    "Panasonic FZ-1",
                    SystemID.THREE_DO,
                    null,
                    "panafz1.bin",
                ),
                Bios(
                    "panafz10.bin",
                    "51F2F43AE2F3508A14D9F56597E2D3CE",
                    "Panasonic FZ-10",
                    SystemID.THREE_DO,
                    null,
                    "panafz10.bin",
                ),
                Bios(
                    "panafz10-norsa.bin",
                    "1477BDA80DC33731A65468C1F5BCBEE9",
                    "Panasonic FZ-10 [RSA Patch]",
                    SystemID.THREE_DO,
                    null,
                    "panafz10-norsa.bin",
                ),
                Bios(
                    "panafz10e-anvil.bin",
                    "A48E6746BD7EDEC0F40CFF078F0BB19F",
                    "Panasonic FZ-10-E [Anvil]",
                    SystemID.THREE_DO,
                    null,
                    "panafz10e-anvil.bin",
                ),
                Bios(
                    "panafz10e-anvil-norsa.bin",
                    "CF11BBB5A16D7AF9875CCA9DE9A15E09",
                    "Panasonic FZ-10-E [Anvil RSA Patch]",
                    SystemID.THREE_DO,
                    null,
                    "panafz10e-anvil-norsa.bin",
                ),
                Bios(
                    "panafz1j.bin",
                    "A496CFDDED3DA562759BE3561317B605",
                    "Panasonic FZ-1J",
                    SystemID.THREE_DO,
                    null,
                    "panafz1j.bin",
                ),
                Bios(
                    "panafz1j-norsa.bin",
                    "F6C71DE7470D16ABE4F71B1444883DC8",
                    "Panasonic FZ-1J [RSA Patch]",
                    SystemID.THREE_DO,
                    null,
                    "panafz1j-norsa.bin",
                ),
                Bios(
                    "goldstar.bin",
                    "8639FD5E549BD6238CFEE79E3E749114",
                    "Goldstar  GDO-101M",
                    SystemID.THREE_DO,
                    null,
                    "goldstar.bin",
                ),
                Bios(
                    "sanyotry.bin",
                    "35FA1A1EBAAEEA286DC5CD15487C13EA",
                    "Sanyo IMP-21J TRY",
                    SystemID.THREE_DO,
                    null,
                    "sanyotry.bin",
                ),
                Bios(
                    "3do_arcade_saot.bin",
                    "8970FC987AB89A7F64DA9F8A8C4333FF",
                    "Shootout At Old Tucson",
                    SystemID.THREE_DO,
                    null,
                    "3do_arcade_saot.bin",
                ),
                Bios(
                    "panafz1-kanji.bin",
                    "B8DC97F778A6245C58E064B0312E8281",
                    "Panasonic FZ-1 Kanji ROM",
                    SystemID.THREE_DO,
                    null,
                    "panafz1-kanji.bin",
                ),
                Bios(
                    "panafz10ja-anvil-kanji.bin",
                    "428577250F43EDC902EA239C50D2240D",
                    "Panasonic FZ-10JA Kanji ROM",
                    SystemID.THREE_DO,
                    null,
                    "panafz10ja-anvil-kanji.bin",
                ),
                Bios(
                    "panafz1j-kanji.bin",
                    "C23FB5D5E6BB1C240D02CF968972BE37",
                    "Panasonic FZ-1J Kanji ROM",
                    SystemID.THREE_DO,
                    null,
                    "panafz1j-kanji.bin",
                ),
                Bios(
                    "colecovision.rom",
                    "2C66F5911E5B42B8EBE113403548EEE7",
                    "ColecoVision OS-7 BIOS - Required",
                    SystemID.COLECOVISION,
                    "3AA93EF3",
                    "colecovision.rom",
                ),
                Bios(
                    "MSX.ROM",
                    "364A1A579FE5CB8DBA54519BCFCDAC0D",
                    "MSX BIOS - Required",
                    SystemID.MSX,
                    null,
                    "MSX.ROM",
                ),
                Bios(
                    "MSX2.ROM",
                    "EC3A01C91F24FBDDCBCAB0AD301BC9EF",
                    "MSX2 BIOS - Required",
                    SystemID.MSX2,
                    null,
                    "MSX2.ROM",
                ),
                Bios(
                    "MSX2EXT.ROM",
                    "2183C2AFF17CF4297BDB496DE78C2E8A",
                    "MSX2 ExtROM - Required",
                    SystemID.MSX2,
                    null,
                    "MSX2EXT.ROM",
                ),
                Bios(
                    "MSX2P.ROM",
                    "847CC025FFAE665487940FF2639540E5",
                    "MSX2+ BIOS - Required",
                    SystemID.MSX,
                    null,
                    "MSX2P.ROM",
                ),
                Bios(
                    "MSX2PEXT.ROM",
                    "7C8243C71D8F143B2531F01AFA6A05DC",
                    "MSX2+ ExtROM - Required",
                    SystemID.MSX,
                    null,
                    "MSX2PEXT.ROM",
                ),
                Bios(
                    "DISK.ROM",
                    "80DCD1AD1A4CF65D64B7BA10504E8190",
                    "DiskROM/BDOS (optional)",
                    SystemID.MSX,
                    null,
                    "DISK.ROM",
                ),
                Bios(
                    "FMPAC.ROM",
                    "6F69CC8B5ED761B03AFD78000DFB0E19",
                    "FMPAC BIOS (optional)",
                    SystemID.MSX,
                    null,
                    "FMPAC.ROM",
                ),
                Bios(
                    "MSXDOS2.ROM",
                    "6418D091CD6907BBCF940324339E43BB",
                    "MSX-DOS 2 (optional)",
                    SystemID.MSX,
                    null,
                    "MSXDOS2.ROM",
                ),
                Bios(
                    "PAINTER.ROM",
                    "403CDEA1CBD2BB24FAE506941F8F655E",
                    "Yamaha Painter (optional)",
                    SystemID.MSX,
                    null,
                    "PAINTER.ROM",
                ),
                Bios(
                    "KANJI.ROM",
                    "FEBE8782B466D7C3B16DE6D104826B34",
                    "Kanji Font (optional)",
                    SystemID.MSX,
                    null,
                    "KANJI.ROM",
                ),
                Bios(
                    "[BIOS] Atari Jaguar CD (World).j64",
                    "77CD95C7AD06A39F4C59995094AA10F9",
                    "Jaguar CD BIOS, retail - optional override",
                    SystemID.JAGUAR,
                    null,
                    "[BIOS] Atari Jaguar CD (World).j64",
                ),
                Bios(
                    "[BIOS] Atari Jaguar Developer CD (World).j64",
                    "578DE34498CB9A40C5368B6F5CA80484",
                    "Jaguar CD BIOS, developer - optional override",
                    SystemID.JAGUAR,
                    null,
                    "[BIOS] Atari Jaguar Developer CD (World).j64",
                ),
                Bios(
                    "scph101.bin",
                    "6E3735FF4C7DC899EE98981385F6F3D0",
                    "PS One 4.5 NTSC-U/C",
                    SystemID.PSX,
                    "171BDCEC",
                ),
                Bios(
                    "scph7001.bin",
                    "1E68C231D0896B7EADCAD1D7D8E76129",
                    "PS Original 4.1 NTSC-U/C",
                    SystemID.PSX,
                    "502224B6",
                ),
                Bios(
                    "scph5501.bin",
                    "490F666E1AFB15B7362B406ED1CEA246",
                    "PS Original 3.0 NTSC-U/C",
                    SystemID.PSX,
                    "8D8CB7E4",
                ),
                Bios(
                    "scph1001.bin",
                    "924E392ED05558FFDB115408C263DCCF",
                    "PS Original 2.2 NTSC-U/C",
                    SystemID.PSX,
                    "37157331",
                ),
                Bios(
                    "lynxboot.img",
                    "FCD403DB69F54290B51035D82F835E7B",
                    "Lynx Boot Image",
                    SystemID.LYNX,
                    "0D973C9D",
                ),
                Bios(
                    "bios_CD_E.bin",
                    "E66FA1DC5820D254611FDCDBA0662372",
                    "Sega CD E",
                    SystemID.SEGACD,
                    "529AC15A",
                ),
                Bios(
                    "bios_CD_J.bin",
                    "278A9397D192149E84E820AC621A8EDD",
                    "Sega CD J",
                    SystemID.SEGACD,
                    "9D2DA8F2",
                ),
                Bios(
                    "bios_CD_U.bin",
                    "2EFD74E3232FF260E371B99F84024F7F",
                    "Sega CD U",
                    SystemID.SEGACD,
                    "C6D10268",
                ),
                Bios(
                    "bios7.bin",
                    "DF692A80A5B1BC90728BC3DFC76CD948",
                    "Nintendo DS ARM7",
                    SystemID.NDS,
                    "1280F0D5",
                ),
                Bios(
                    "bios9.bin",
                    "A392174EB3E572FED6447E956BDE4B25",
                    "Nintendo DS ARM9",
                    SystemID.NDS,
                    "2AB23573",
                ),
                Bios(
                    "firmware.bin",
                    "E45033D9B0FA6B0DE071292BBA7C9D13",
                    "Nintendo DS Firmware",
                    SystemID.NDS,
                    "945F9DC9",
                    "nds_firmware.bin",
                ),
                Bios(
                    "scph39001.bin",
                    "D5CE2C7D119F563CE04BC04571DE9B9F",
                    "PlayStation 2 (SCPH-39001)",
                    SystemID.PS2,
                    "0220C2F9",
                ),
                Bios(
                    "scph39004.bin",
                    "C9A0F7E04C74E2FA1E094F94FFCFBFD8",
                    "PlayStation 2 Europe (SCPH-39004)",
                    SystemID.PS2,
                    "98D4D6B6",
                ),
                Bios(
                    "scph39000.bin",
                    "D6846CF87CA7C7F30A3C910FD5F1D328",
                    "PlayStation 2 Japan (SCPH-39000)",
                    SystemID.PS2,
                    "46FB0EA2",
                ),
                Bios(
                    "scph30000.bin",
                    "815AC991D8BC3B364696BEAD3457DE7D",
                    "PlayStation 2 Japan (SCPH-30000)",
                    SystemID.PS2,
                    "4FC3B495",
                ),
                Bios(
                    "scph70012.bin",
                    "D333558CC14561C1FDC334C0C34137A5",
                    "PlayStation 2 Slim (SCPH-70012)",
                    SystemID.PS2,
                    "1B6E631A",
                ),
                Bios(
                    "scph77001.bin",
                    "BF7E4EAF60459DB6182B11C865E9AECE",
                    "PlayStation 2 Slim (SCPH-77001)",
                    SystemID.PS2,
                    "0B27DB79",
                ),
                Bios(
                    "dc/dc_boot.bin",
                    "E10C53C2F8B90BAB96EAD2D368858623",
                    "Dreamcast BIOS",
                    SystemID.DREAMCAST,
                    null,
                    "dc_boot.bin",
                ),
            )
    }
}
