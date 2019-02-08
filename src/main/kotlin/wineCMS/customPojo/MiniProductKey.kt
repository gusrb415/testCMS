package wineCMS.customPojo

import wineCMS.pojo.VmDafdPdpkPriceStock

class MiniProductKey(val wineRefEXT: String?, val vintage: Short?){
    override fun hashCode(): Int {
        var number = 930415
        number = number * 27 + (wineRefEXT?.hashCode() ?: 0)
        number = number * 28 + (vintage?.hashCode() ?: 0)
        return number
    }

    override fun equals(other: Any?) : Boolean {
        return !(other !is MiniProductKey || wineRefEXT != other.wineRefEXT || vintage != other.vintage)
    }

    constructor(packageData: VmDafdPdpkPriceStock) : this(packageData.wineRefEXT,packageData.vintageTag)
}