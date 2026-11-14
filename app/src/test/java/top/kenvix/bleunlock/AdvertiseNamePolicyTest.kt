package top.kenvix.bleunlock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvertiseNamePolicyTest {
    @Test fun shortNamesFitLegacyScanResponse() {
        assertTrue(AdvertiseNamePolicy.canIncludeName("Pixel 9 Pro"))
        assertTrue(AdvertiseNamePolicy.canIncludeName("蓝牙手机"))
        assertTrue(AdvertiseNamePolicy.canIncludeName("A".repeat(29)))
    }

    @Test fun oversizedOrUnavailableNamesUseServiceUuidFallback() {
        assertFalse(AdvertiseNamePolicy.canIncludeName("A".repeat(30)))
        assertFalse(AdvertiseNamePolicy.canIncludeName("蓝牙手机设备名称过长123"))
        assertFalse(AdvertiseNamePolicy.canIncludeName(""))
        assertFalse(AdvertiseNamePolicy.canIncludeName("  "))
        assertFalse(AdvertiseNamePolicy.canIncludeName(null))
        assertFalse(AdvertiseNamePolicy.canIncludeName("Phone\nName"))
    }
}