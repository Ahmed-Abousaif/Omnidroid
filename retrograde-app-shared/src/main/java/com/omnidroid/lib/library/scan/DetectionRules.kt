package com.omnidroid.lib.library.scan

import com.omnidroid.lib.library.SystemID

data class ScanInput(
    val name: String,
    val size: Long,
    val path: String?,
    val open: () -> RandomAccessBytes?,
    val openNamed: (String) -> RandomAccessBytes? = { null },
    val openChd: ChdOpener? = null,
    val openNamedChd: (String) -> ChdOpener? = { null },
)

sealed class Detection {
    data class Ready(
        val system: SystemID,
        val key: MetadataKey?,
        val archiveEntry: String? = null,
    ) : Detection()

    data class HashedBin(
        val crc: String,
        val archiveEntry: String? = null,
    ) : Detection()

    data class Arcade(
        val setName: String,
        val memberCrcs: List<String>,
    ) : Detection()

    data class Rejected(
        val reason: String,
    ) : Detection()
}

object DetectionRules {
    private const val BIN_HASH_LIMIT = 16L * 1024 * 1024

    private val cartridge =
        mapOf(
            "nes" to (SystemID.NES to HashMode.RAW),
            "smc" to (SystemID.SNES to HashMode.SNES),
            "sfc" to (SystemID.SNES to HashMode.SNES),
            "fig" to (SystemID.SNES to HashMode.SNES),
            "swc" to (SystemID.SNES to HashMode.SNES),
            "bs" to (SystemID.SNES to HashMode.SNES),
            "sms" to (SystemID.SMS to HashMode.RAW),
            "gg" to (SystemID.GG to HashMode.RAW),
            "gb" to (SystemID.GB to HashMode.RAW),
            "gbc" to (SystemID.GBC to HashMode.RAW),
            "gba" to (SystemID.GBA to HashMode.RAW),
            "ngp" to (SystemID.NGP to HashMode.RAW),
            "ngc" to (SystemID.NGC to HashMode.RAW),
            "ws" to (SystemID.WS to HashMode.RAW),
            "wsc" to (SystemID.WSC to HashMode.RAW),
            "a26" to (SystemID.ATARI2600 to HashMode.RAW),
            "a78" to (SystemID.ATARI7800 to HashMode.RAW),
            "lnx" to (SystemID.LYNX to HashMode.RAW),
            "pce" to (SystemID.PC_ENGINE to HashMode.RAW),
            "gen" to (SystemID.GENESIS to HashMode.RAW),
            "md" to (SystemID.GENESIS to HashMode.RAW),
            "smd" to (SystemID.GENESIS to HashMode.SMD),
            "n64" to (SystemID.N64 to HashMode.N64),
            "z64" to (SystemID.N64 to HashMode.N64),
            "v64" to (SystemID.N64 to HashMode.N64),
        )

    private val fileNameSystems =
        mapOf(
            "cdi" to SystemID.DREAMCAST,
            "gdi" to SystemID.DREAMCAST,
            "lst" to SystemID.DREAMCAST,
            "dosz" to SystemID.DOS,
            "wad" to SystemID.WII,
            "wud" to SystemID.WII_U,
            "wux" to SystemID.WII_U,
            "wua" to SystemID.WII_U,
            "rpx" to SystemID.WII_U,
            "irx" to SystemID.PS2,
        )

    fun identify(
        input: ScanInput,
        beforeExpensiveHash: (SystemID) -> Unit = {},
    ): Detection {
        return runCatching { identifyUnsafe(input, beforeExpensiveHash) }
            .getOrElse { Detection.Rejected("Could not read ${input.name}") }
    }

    private fun identifyUnsafe(
        input: ScanInput,
        beforeExpensiveHash: (SystemID) -> Unit,
    ): Detection {
        val extension = ScanKeys.extension(input.name)
        cartridge[extension]?.let { (system, mode) ->
            return hashLoose(input, system, mode, beforeExpensiveHash)
        }
        fileNameSystems[extension]?.let { system ->
            return Detection.Ready(system, romBase(input.name))
        }
        return when (extension) {
            "nds" -> headerSerial(input, SystemID.NDS) { it.ascii(0x0C, 4).takeIf(::isCode) }
            "3ds", "cci" -> headerSerial(input, SystemID.NINTENDO_3DS) { it.ascii(0x1150, 10).ifBlank { null } }
            "gcm" -> nintendo(input) { NintendoDisc.fromIso(it) }
            "tgc" -> nintendo(input) { NintendoDisc.fromTgc(it) }
            "wbfs" -> nintendo(input) { NintendoDisc.fromWbfs(it) }
            "gcz" -> nintendo(input) { NintendoDisc.fromGcz(it) }
            "rvz", "wia" -> nintendo(input) { NintendoDisc.fromRvz(it) }
            "ciso" -> nintendo(input) { NintendoDisc.fromCiso(it) }
            "iso" -> iso(input)
            "bin" -> bin(input)
            "cue" -> cue(input)
            "chd" -> chd(input)
            "cso", "zso" -> compressedDisc(input)
            "gz" -> gzipDisc(input)
            "mdf" -> flatDisc(input, 0)
            "pbp" -> pbp(input)
            "m3u" -> m3u(input, beforeExpensiveHash)
            "elf" -> elf(input)
            "dol" -> folderOnly(input, setOf(SystemID.GAMECUBE, SystemID.WII), ".dol can be GameCube or Wii")
            "zip" -> zip(input, beforeExpensiveHash)
            else -> Detection.Rejected("Unsupported file")
        }
    }

    private fun hashLoose(
        input: ScanInput,
        system: SystemID,
        mode: HashMode,
        beforeExpensiveHash: (SystemID) -> Unit,
    ): Detection {
        beforeExpensiveHash(system)
        val source = input.open() ?: return Detection.Rejected("Could not open ${input.name}")
        source.use {
            val crc = CartridgeHasher.hash(it, mode)
            return Detection.Ready(system, MetadataKey(MetadataKeyType.CRC, crc))
        }
    }

    private fun headerSerial(
        input: ScanInput,
        system: SystemID,
        readCode: (RandomAccessBytes) -> String?,
    ): Detection {
        val source = input.open() ?: return Detection.Rejected("Could not open ${input.name}")
        source.use {
            val serial = readCode(it)?.trim()?.uppercase()
            return Detection.Ready(system, serial?.let { value -> MetadataKey(MetadataKeyType.SERIAL, value) })
        }
    }

    private fun nintendo(
        input: ScanInput,
        read: (RandomAccessBytes) -> NintendoDiscId?,
    ): Detection {
        val source = input.open() ?: return Detection.Rejected("Could not open ${input.name}")
        source.use {
            val id = read(it) ?: return Detection.Rejected("${input.name} is not a GameCube or Wii disc")
            return Detection.Ready(id.system, id.code?.let { code -> MetadataKey(MetadataKeyType.CODE, code) })
        }
    }

    private fun iso(input: ScanInput): Detection {
        val source = input.open() ?: return Detection.Rejected("Could not open ${input.name}")
        source.use { bytes ->
            NintendoDisc.fromIso(bytes)?.let { id ->
                return Detection.Ready(id.system, id.code?.let { MetadataKey(MetadataKeyType.CODE, it) })
            }
            identifyFlat(bytes, input.name, 0)?.let { return it }
        }
        return folderOrReject(input, ".iso was not a recognised disc")
    }

    private fun bin(input: ScanInput): Detection {
        val source = input.open() ?: return Detection.Rejected("Could not open ${input.name}")
        source.use { bytes ->
            if (isDisc(bytes)) {
                identifyFlat(bytes, input.name, 0)?.let { return it }
                return folderOrReject(input, ".bin was not a recognised disc")
            }
            if (isGenesis(bytes)) {
                val crc = CartridgeHasher.hash(bytes, HashMode.RAW)
                return Detection.Ready(SystemID.GENESIS, MetadataKey(MetadataKeyType.CRC, crc))
            }
            if (isInes(bytes)) {
                val crc = CartridgeHasher.hash(bytes, HashMode.RAW)
                return Detection.Ready(SystemID.NES, MetadataKey(MetadataKeyType.CRC, crc))
            }
            if (input.size > BIN_HASH_LIMIT) {
                return Detection.Rejected(".bin over 16 MB was not a disc image")
            }
            val crc = CartridgeHasher.hash(bytes, HashMode.RAW)
            return Detection.HashedBin(crc)
        }
    }

    private fun isGenesis(source: RandomAccessBytes): Boolean {
        val name = source.ascii(0x100, 16).uppercase()
        return name.startsWith("SEGA GENESIS") || name.startsWith("SEGA MEGA DRIVE") || name.startsWith("SEGA 32X")
    }

    private fun isInes(source: RandomAccessBytes): Boolean {
        val head = source.read(0, 4)
        return head.size >= 4 &&
            head[0] == 'N'.code.toByte() &&
            head[1] == 'E'.code.toByte() &&
            head[2] == 'S'.code.toByte() &&
            head[3] == 0x1A.toByte()
    }

    private fun isDisc(source: RandomAccessBytes): Boolean {
        val head = source.read(0, 12)
        if (head.size >= 12 && head[0] == 0.toByte() && head[1] == 0xFF.toByte()) return true
        val marker = source.read(0x8001, 5)
        return marker.size >= 5 && marker.startsWithAscii("CD001")
    }

    private fun cue(input: ScanInput): Detection {
        val sheet = input.open()?.use { it.read(0, minOf(it.size, 256 * 1024).toInt()).toString(Charsets.UTF_8) }
            ?: return Detection.Rejected("Could not read cue sheet")
        val track = cueDataTrack(sheet)?.substringAfterLast('/')?.substringAfterLast('\\')
            ?: return Detection.Rejected("Cue sheet has no data track")
        val bin = input.openNamed(track) ?: return Detection.Rejected("Cue data track $track is missing")
        bin.use { identifyFlat(it, input.name, 0)?.let { return it } }
        return Detection.Rejected("Cue data track was not a recognised disc")
    }

    private fun chd(input: ScanInput): Detection {
        val supported = input.open()?.use { ChdHeader.isVersion5(it) } ?: false
        if (!supported) return Detection.Rejected("Only CHD version 5 is supported")
        val chd = input.openChd?.open() ?: return Detection.Rejected("CHD could not be opened")
        chd.use { sectors ->
            if (sectors.dreamcast) {
                return Detection.Ready(SystemID.DREAMCAST, romBase(input.name))
            }
            val hit =
                DiscIdentifier.identify(sectors)
                    ?: DiscIdentifier.identify(
                        object : UserSectors {
                            override fun readUserSector(lba: Int): ByteArray? = sectors.readUserSector(lba + 150)
                        },
                    )
            return hit?.toDetection(input.name) ?: Detection.Rejected("CHD was not a recognised disc")
        }
    }

    private fun compressedDisc(input: ScanInput): Detection {
        val source = input.open() ?: return Detection.Rejected("Could not open ${input.name}")
        source.use { bytes ->
            val sectors = CsoImage.open(bytes) ?: return Detection.Rejected("${input.name} is not a CSO or ZSO image")
            return DiscIdentifier.identify(sectors)?.toDetection(input.name)
                ?: Detection.Rejected("${input.name} was not a recognised disc")
        }
    }

    private fun gzipDisc(input: ScanInput): Detection {
        val source = input.open() ?: return Detection.Rejected("Could not open ${input.name}")
        source.use { bytes ->
            val decoded = GzipImage.open(bytes) ?: return Detection.Rejected("${input.name} is not a gzip disc")
            decoded.use { identifyFlat(it, input.name, 0)?.let { hit -> return hit } }
        }
        return Detection.Rejected("${input.name} was not a recognised disc")
    }

    private fun flatDisc(
        input: ScanInput,
        offset: Long,
    ): Detection {
        val source = input.open() ?: return Detection.Rejected("Could not open ${input.name}")
        source.use {
            return identifyFlat(it, input.name, offset)
                ?: Detection.Rejected("${input.name} was not a recognised disc")
        }
    }

    private fun identifyFlat(
        source: RandomAccessBytes,
        fileName: String,
        offset: Long,
    ): Detection? {
        val layout = DiscImages.layout(source, offset)
        val hit = DiscIdentifier.identify(DiscImages.userSectors(source, layout)) ?: return null
        return hit.toDetection(fileName)
    }

    private fun pbp(input: ScanInput): Detection {
        val source = input.open() ?: return Detection.Rejected("Could not open ${input.name}")
        source.use { bytes ->
            val info = PbpReader.read(bytes) ?: return Detection.Rejected("${input.name} is not a PBP")
            return Detection.Ready(info.system, info.serial?.let { MetadataKey(MetadataKeyType.SERIAL, it) })
        }
    }

    private fun m3u(
        input: ScanInput,
        beforeExpensiveHash: (SystemID) -> Unit,
    ): Detection {
        val text = input.open()?.use { it.read(0, minOf(it.size, 64 * 1024).toInt()).toString(Charsets.UTF_8) }
            ?: return Detection.Rejected("Could not read playlist")
        val first = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            ?: return Detection.Rejected("Playlist is empty")
        val name = first.substringAfterLast('/').substringAfterLast('\\')
        val child = input.openNamed(name) ?: return Detection.Rejected("Playlist entry $name is missing")
        return identify(
            ScanInput(
                name = name,
                size = child.size,
                path = input.path,
                open = { child },
                openNamed = input.openNamed,
                openChd = input.openNamedChd(name),
                openNamedChd = input.openNamedChd,
            ),
            beforeExpensiveHash,
        )
    }

    private fun elf(input: ScanInput): Detection {
        val source = input.open() ?: return Detection.Rejected("Could not open ${input.name}")
        val machine = source.use { ElfHeader.machine(it) }
        return when (machine) {
            ElfHeader.MACHINE_MIPS -> Detection.Ready(SystemID.PS2, romBase(input.name))
            ElfHeader.MACHINE_PPC ->
                folderOnly(
                    input,
                    setOf(SystemID.GAMECUBE, SystemID.WII, SystemID.WII_U),
                    "PowerPC .elf can be GameCube, Wii or Wii U",
                )
            else -> Detection.Rejected("${input.name} is not a recognised ELF")
        }
    }

    private val archiveExtensions =
        cartridge.keys +
            fileNameSystems.keys +
            setOf(
                "nds",
                "3ds",
                "cci",
                "gcm",
                "tgc",
                "wbfs",
                "gcz",
                "rvz",
                "wia",
                "ciso",
                "iso",
                "cso",
                "zso",
                "gz",
                "mdf",
                "pbp",
                "elf",
                "dol",
            )

    private fun zip(
        input: ScanInput,
        beforeExpensiveHash: (SystemID) -> Unit,
    ): Detection {
        val source = input.open() ?: return Detection.Rejected("Could not open ${input.name}")
        source.use { bytes ->
            val entries = ZipDirectory.read(bytes) ?: return Detection.Rejected("${input.name} is not a zip archive")
            val game = chooseArchiveGame(entries, input.name)
            if (game != null) {
                return identifyArchiveEntry(input, bytes, game, beforeExpensiveHash)
            }
            val setName =
                input.name
                    .substringAfterLast('/')
                    .substringAfterLast('\\')
                    .lowercase()
            val crcs = entries.map { it.crc }.distinct()
            return Detection.Arcade(setName, crcs)
        }
    }

    private fun chooseArchiveGame(
        entries: List<ZipEntryInfo>,
        zipName: String,
    ): ZipEntryInfo? {
        val files = entries.filter { !it.name.endsWith("/") && it.uncompressedSize > 0 && it.extension != "zip" }
        val games = files.filter { it.extension in archiveExtensions }
        if (games.isNotEmpty()) {
            val zipBase = ScanKeys.romBase(zipName.substringAfterLast('/').substringAfterLast('\\'))
            val named =
                games.filter {
                    ScanKeys.romBase(it.name.substringAfterLast('/').substringAfterLast('\\')) == zipBase
                }
            return (named.ifEmpty { games }).maxByOrNull { it.uncompressedSize }
        }
        val bins = files.filter { it.extension == "bin" }
        if (bins.size != 1) return null
        val only = bins.first()
        val total = files.sumOf { it.uncompressedSize }
        if (files.size == 1 || (total > 0 && only.uncompressedSize.toDouble() / total.toDouble() > 0.9)) {
            return only
        }
        return null
    }

    private fun identifyArchiveEntry(
        input: ScanInput,
        bytes: RandomAccessBytes,
        entry: ZipEntryInfo,
        beforeExpensiveHash: (SystemID) -> Unit,
    ): Detection {
        cartridge[entry.extension]?.let { (system, mode) ->
            beforeExpensiveHash(system)
            val crc =
                if (
                    CartridgeHasher.needsNormalisedHash(entry.extension, entry.uncompressedSize) ||
                        entry.crc == "00000000"
                ) {
                    ZipEntryHasher.hash(bytes, entry, mode)
                } else {
                    entry.crc
                }
            return Detection.Ready(system, crc?.let { MetadataKey(MetadataKeyType.CRC, it) }, entry.name)
        }
        val nestedName = entry.name.substringAfterLast('/').substringAfterLast('\\')
        val result =
            identify(
                ScanInput(
                    name = nestedName,
                    size = entry.uncompressedSize,
                    path = input.path,
                    open = { ZipEntryHasher.open(bytes, entry) },
                    openNamed = input.openNamed,
                    openChd = null,
                    openNamedChd = input.openNamedChd,
                ),
                beforeExpensiveHash,
            )
        return when (result) {
            is Detection.Ready -> result.copy(archiveEntry = entry.name)
            is Detection.HashedBin -> result.copy(archiveEntry = entry.name)
            else -> result
        }
    }

    private fun folderOnly(
        input: ScanInput,
        allowed: Set<SystemID>,
        reason: String,
    ): Detection {
        val system = FolderHints.systemAmong(input.path, allowed)
            ?: return Detection.Rejected("Needs a console folder: $reason")
        return Detection.Ready(system, romBase(input.name))
    }

    private fun folderOrReject(
        input: ScanInput,
        reason: String,
    ): Detection {
        val system = FolderHints.system(input.path) ?: return Detection.Rejected(reason)
        return Detection.Ready(system, romBase(input.name))
    }

    private fun romBase(name: String): MetadataKey {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        return MetadataKey(MetadataKeyType.ROM_BASE, ScanKeys.romBase(base))
    }

    private fun isCode(value: String): Boolean {
        return value.length == 4 && value.all { it.isLetterOrDigit() }
    }

    private fun cueDataTrack(sheet: String): String? {
        val lines = sheet.lines()
        val highDensity = lines.indexOfFirst { it.contains("HIGH-DENSITY AREA", ignoreCase = true) }
        val region = if (highDensity >= 0) lines.drop(highDensity + 1) else lines
        val file = Regex("FILE\\s+\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        if (highDensity >= 0) {
            return region.firstNotNullOfOrNull { file.find(it)?.groupValues?.get(1)?.substringAfterLast('/')?.substringAfterLast('\\') }
        }
        val dataLine =
            region.indexOfFirst {
                it.contains("MODE1", ignoreCase = true) || it.contains("MODE2", ignoreCase = true)
            }
        if (dataLine >= 0) {
            for (index in dataLine downTo 0) {
                file.find(region[index])?.groupValues?.get(1)?.let { return it.substringAfterLast('/').substringAfterLast('\\') }
            }
        }
        return region.firstNotNullOfOrNull { file.find(it)?.groupValues?.get(1)?.substringAfterLast('/')?.substringAfterLast('\\') }
    }
}

private fun DiscHit.toDetection(fileName: String): Detection {
    val key =
        when {
            serial != null -> MetadataKey(MetadataKeyType.SERIAL, serial)
            system == SystemID.DREAMCAST -> MetadataKey(MetadataKeyType.ROM_BASE, ScanKeys.romBase(fileName))
            else -> null
        }
    return Detection.Ready(system, key)
}
