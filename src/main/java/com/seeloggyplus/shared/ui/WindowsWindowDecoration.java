package com.seeloggyplus.shared.ui;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.win32.StdCallLibrary;

final class WindowsWindowDecoration {
    private interface DwmApi extends StdCallLibrary {
        int DwmSetWindowAttribute(HWND window, int attribute, Pointer value, int size);
    }

    private static final class WindowsApi {
        private static final DwmApi DWM = Native.load("dwmapi", DwmApi.class);
    }

    private WindowsWindowDecoration() { }

    static boolean accepts(long pid, long ownPid, String className, int style) {
        return pid == ownPid && className.matches("GlassWndClass-GlassWindowClass-[0-9]+")
                && (style & 0x00c00000) == 0x00c00000;
    }

    static void apply(boolean dark) {
        if (!Platform.isWindows() || "Monocle".equalsIgnoreCase(System.getProperty("glass.platform"))) return;
        var dwm = WindowsApi.DWM;
        var user = User32.INSTANCE;
        var value = new IntByReference(dark ? 1 : 0);
        long ownPid = ProcessHandle.current().pid();
        var failure = new IntByReference();
        user.EnumWindows((window, data) -> {
            var pid = new IntByReference();
            user.GetWindowThreadProcessId(window, pid);
            if (Integer.toUnsignedLong(pid.getValue()) != ownPid) return true;
            char[] name = new char[256];
            user.GetClassName(window, name, name.length);
            int style = user.GetWindowLong(window, WinUser.GWL_STYLE);
            if (!accepts(Integer.toUnsignedLong(pid.getValue()), ownPid, Native.toString(name), style)) return true;
            int result = dwm.DwmSetWindowAttribute(window, 20, value.getPointer(), 4);
            if (result < 0) failure.setValue(result);
            return true;
        }, null);
        if (failure.getValue() < 0) throw new IllegalStateException("DWM rejected window theme: " + Integer.toHexString(failure.getValue()));
    }
}
