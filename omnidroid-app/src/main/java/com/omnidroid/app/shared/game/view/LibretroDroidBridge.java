package com.omnidroid.app.shared.game.view;

import android.os.ParcelFileDescriptor;
import com.swordfish.libretrodroid.DetachedVirtualFile;
import com.swordfish.libretrodroid.GLRetroShader;
import com.swordfish.libretrodroid.ImmersiveMode;
import com.swordfish.libretrodroid.LibretroDroid;
import com.swordfish.libretrodroid.ShaderConfig;
import com.swordfish.libretrodroid.Variable;
import com.swordfish.libretrodroid.VirtualFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LibretroDroidBridge {

    private LibretroDroidBridge() {}

    public static GLRetroShader buildShader(ShaderConfig shaderConfig) {
        int shaderType = LibretroDroid.SHADER_DEFAULT;
        if (shaderConfig instanceof ShaderConfig.CRT) {
            shaderType = LibretroDroid.SHADER_CRT;
        } else if (shaderConfig instanceof ShaderConfig.LCD) {
            shaderType = LibretroDroid.SHADER_LCD;
        } else if (shaderConfig instanceof ShaderConfig.Sharp) {
            shaderType = LibretroDroid.SHADER_SHARP;
        } else if (shaderConfig instanceof ShaderConfig.CUT) {
            shaderType = LibretroDroid.SHADER_UPSCALE_CUT;
        } else if (shaderConfig instanceof ShaderConfig.CUT2) {
            shaderType = LibretroDroid.SHADER_UPSCALE_CUT2;
        } else if (shaderConfig instanceof ShaderConfig.CUT3) {
            shaderType = LibretroDroid.SHADER_UPSCALE_CUT3;
        }
        return new GLRetroShader(shaderType, Collections.emptyMap());
    }

    public static void create(
            int contextVersion,
            String coreFilePath,
            String systemDirectory,
            String savesDirectory,
            Variable[] variables,
            ShaderConfig shaderConfig,
            float refreshRate,
            boolean preferLowLatencyAudio,
            boolean enableVirtualFileSystem,
            boolean enableMicrophone,
            boolean skipDuplicateFrames,
            ImmersiveMode immersiveMode,
            String language
    ) {
        GLRetroShader shader = buildShader(shaderConfig);
        LibretroDroid.create(
                contextVersion,
                coreFilePath,
                systemDirectory,
                savesDirectory,
                variables,
                shader,
                refreshRate,
                preferLowLatencyAudio,
                enableVirtualFileSystem,
                enableMicrophone,
                skipDuplicateFrames,
                immersiveMode,
                language
        );
    }

    public static void setShaderConfig(ShaderConfig shaderConfig) {
        GLRetroShader shader = buildShader(shaderConfig);
        LibretroDroid.setShaderConfig(shader);
    }

    public static void loadGameFromVirtualFiles(List<VirtualFile> virtualFiles) {
        if (virtualFiles == null) return;
        List<DetachedVirtualFile> detached = new ArrayList<>();
        for (VirtualFile vf : virtualFiles) {
            ParcelFileDescriptor pfd = vf.getFileDescriptor();
            int fd = pfd != null ? pfd.detachFd() : -1;
            detached.add(new DetachedVirtualFile(vf.getVirtualPath(), fd));
        }
        LibretroDroid.loadGameFromVirtualFiles(detached);
    }
}
