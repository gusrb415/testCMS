package wineCMS.customPojo

import wineCMS.pojo.VmDafdPdpkPriceStock

class MiniPackageKey(val productKey: MiniProductKey, private val packageType: String?, private val packageRefEXT: String?) {
    override fun hashCode(): Int {
        var number = 930415
        number = number * 29 + productKey.hashCode()
        number = number * 30 + (packageType?.hashCode() ?: 0)
        number = number * 31 + (packageRefEXT?.hashCode() ?: 0)
        return number
    }

    override fun equals(other: Any?) : Boolean {
        return !(other !is MiniPackageKey || productKey != other.productKey ||
                packageType != other.packageType || packageRefEXT != other.packageRefEXT)
    }

    constructor(packageData: VmDafdPdpkPriceStock) : this(MiniProductKey(packageData), packageData.pckgType, packageData.pckgRefEXT)
}