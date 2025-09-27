use std::fs::File;
use std::io::Write;
use std::mem;
use std::os::fd::{FromRawFd, RawFd};

use base::{debug, Utf8CStr};

pub fn inject_magisk_rc(fd: RawFd, tmp_dir: &Utf8CStr) {
    debug!("Injecting magisk rc");

    let mut file = unsafe { File::from_raw_fd(fd) };

    write!(
        file,
        r#"
on early-init
    write /sys/fs/selinux/enforce 0

on post-fs-data
    start logd
    exec {0} 0 0 -- {1}/magisk --post-fs-data
    exec u:r:magisk:s0 0 0 -- /system/bin/sh -c "setenforce 0"
    setprop ro.build.type userdebug
    setprop ctl.restart adbd
    start adbd

on property:vold.decrypt=trigger_restart_framework
    exec {0} 0 0 -- {1}/magisk --service

on nonencrypted
    exec {0} 0 0 -- {1}/magisk --service

on property:sys.boot_completed=1
    exec {0} 0 0 -- {1}/magisk --boot-complete
    exec u:r:magiskl:s0 0 0 -- /system/bin/sh -c "settings put global adb_enabled 1"
    exec u:r:magisk:s0 0 0 -- /system/bin/sh -c "settings put global development_settings_enabled 1"

on property:init.svc.zygote=stopped
    exec {0} 0 0 -- {1}/magisk --zygote-restart
"#,
        "u:r:magisk:s0", tmp_dir
    )
    .ok();

    mem::forget(file)
}
