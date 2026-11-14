import top.kenvix.bleunlock.AdvertiseNamePolicy
fun main() {
    check(AdvertiseNamePolicy.canIncludeName("Pixel 9 Pro"))
    check(AdvertiseNamePolicy.canIncludeName("蓝牙手机"))
    check(!AdvertiseNamePolicy.canIncludeName("A".repeat(30)))
    check(!AdvertiseNamePolicy.canIncludeName(null))
    check(!AdvertiseNamePolicy.canIncludeName(" "))
    println("BLE scan response name length checks passed")
}