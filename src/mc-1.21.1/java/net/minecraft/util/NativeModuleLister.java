package net.minecraft.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.logging.LogUtils;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.Version;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.Tlhelp32.MODULEENTRY32W;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import net.minecraft.CrashReportCategory;
import org.slf4j.Logger;

public class NativeModuleLister {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int LANG_MASK = 65535;
    private static final int DEFAULT_LANG = 1033;
    private static final int CODEPAGE_MASK = -65536;
    private static final int DEFAULT_CODEPAGE = 78643200;

    public static List<NativeModuleLister.NativeModuleInfo> listModules() {
        if (!Platform.isWindows()) {
            return ImmutableList.of();
        }

        int i = Kernel32.INSTANCE.GetCurrentProcessId();
        Builder<NativeModuleLister.NativeModuleInfo> builder = ImmutableList.builder();

        for (MODULEENTRY32W mODULEENTRY32W : Kernel32Util.getModules(i)) {
            String string = mODULEENTRY32W.szModule();
            Optional<NativeModuleLister.NativeModuleVersion> optional = tryGetVersion(mODULEENTRY32W.szExePath());
            builder.add(new NativeModuleLister.NativeModuleInfo(string, optional));
        }

        return builder.build();
    }

    private static Optional<NativeModuleLister.NativeModuleVersion> tryGetVersion(String filename) {
        try {
            IntByReference intByReference = new IntByReference();
            int i = Version.INSTANCE.GetFileVersionInfoSize(filename, intByReference);
            if (i == 0) {
                int j = Native.getLastError();
                if (j != 1813 && j != 1812) {
                    throw new Win32Exception(j);
                } else {
                    return Optional.empty();
                }
            } else {
                Pointer pointer = new Memory(i);
                if (!Version.INSTANCE.GetFileVersionInfo(filename, 0, i, pointer)) {
                    throw new Win32Exception(Native.getLastError());
                }

                IntByReference intByReference2 = new IntByReference();
                Pointer pointer2 = queryVersionValue(pointer, "\\VarFileInfo\\Translation", intByReference2);
                int[] is = pointer2.getIntArray(0L, intByReference2.getValue() / 4);
                OptionalInt optionalInt = findLangAndCodepage(is);
                if (optionalInt.isEmpty()) {
                    return Optional.empty();
                }

                int k = optionalInt.getAsInt();
                int l = k & 65535;
                int m = (k & -65536) >> 16;
                String string = queryVersionString(pointer, langTableKey("FileDescription", l, m), intByReference2);
                String string2 = queryVersionString(pointer, langTableKey("CompanyName", l, m), intByReference2);
                String string3 = queryVersionString(pointer, langTableKey("FileVersion", l, m), intByReference2);
                return Optional.of(new NativeModuleLister.NativeModuleVersion(string, string3, string2));
            }
        } catch (Exception exception) {
            LOGGER.info("Failed to find module info for {}", filename, exception);
            return Optional.empty();
        }
    }

    private static String langTableKey(String key, int lang, int codepage) {
        return String.format(Locale.ROOT, "\\StringFileInfo\\%04x%04x\\%s", lang, codepage, key);
    }

    private static OptionalInt findLangAndCodepage(int[] versionValue) {
        OptionalInt optionalInt = OptionalInt.empty();

        for (int i : versionValue) {
            if ((i & -65536) == 78643200 && (i & 65535) == 1033) {
                return OptionalInt.of(i);
            }

            optionalInt = OptionalInt.of(i);
        }

        return optionalInt;
    }

    private static Pointer queryVersionValue(Pointer block, String subBlock, IntByReference size) {
        PointerByReference pointerByReference = new PointerByReference();
        if (!Version.INSTANCE.VerQueryValue(block, subBlock, pointerByReference, size)) {
            throw new UnsupportedOperationException("Can't get version value " + subBlock);
        } else {
            return pointerByReference.getValue();
        }
    }

    private static String queryVersionString(Pointer block, String subBlock, IntByReference size) {
        try {
            Pointer pointer = queryVersionValue(block, subBlock, size);
            byte[] bs = pointer.getByteArray(0L, (size.getValue() - 1) * 2);
            return new String(bs, StandardCharsets.UTF_16LE);
        } catch (Exception exception) {
            return "";
        }
    }

    public static void addCrashSection(CrashReportCategory crashSection) {
        crashSection.setDetail(
            "Modules",
            () -> listModules()
                .stream()
                .sorted(Comparator.comparing(nativeModuleInfo -> nativeModuleInfo.name))
                .map(nativeModuleInfo -> "\n\t\t" + nativeModuleInfo)
                .collect(Collectors.joining())
        );
    }

    public static class NativeModuleInfo {
        public final String name;
        public final Optional<NativeModuleLister.NativeModuleVersion> version;

        public NativeModuleInfo(String name, Optional<NativeModuleLister.NativeModuleVersion> version) {
            this.name = name;
            this.version = version;
        }

        @Override
        public String toString() {
            return this.version.<String>map(nativeModuleVersion -> this.name + ":" + nativeModuleVersion).orElse(this.name);
        }
    }

    public static class NativeModuleVersion {
        public final String description;
        public final String version;
        public final String company;

        public NativeModuleVersion(String description, String version, String company) {
            this.description = description;
            this.version = version;
            this.company = company;
        }

        @Override
        public String toString() {
            return this.description + ":" + this.version + ":" + this.company;
        }
    }
}
