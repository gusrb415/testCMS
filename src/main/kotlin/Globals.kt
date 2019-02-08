import com.google.gson.GsonBuilder
import wineCMS.customPojo.*
import wineCMS.pojo.*
import wineCMS.wineAPI.*

val log = Logger

fun getJson(any: Any?): String {
    return GsonBuilder().setPrettyPrinting().create().toJson(any)
}

fun main(args: Array<String>) {
//    val suppliers = Helper(false, "Testing Data Original.xlsx").dayZeroData
//    println(getJson(suppliers))
//    for(i in 0..10)
//        println(suppliers.get(i)?.newWineCount)
//    println(zero)
//    println(getJson(suppliers))
//    println(suppliers.size)
//    suppliers.forEach{
//        println(it.wineList.size)
//    }
//    val supplierSeo = suppliers[0].supplierID
//    val productId = 3136.toLong()
//    val packageId = 3237.toLong()
//    val productId = 3190.toLong()
//    var wineSeo = "crater-rim-test-waipara-sauvignon-blanc"
//    wineSeo = "ruinart-fakewine"
//    wineSeo = "penfolds-bottega-moscato-sparkling"
//    wineSeo = "chateau-talbot-surh-cellers-pinor-noir"
//    wineSeo = "chateau-talbot-bottega-moscato-sparkling"
//    val wineSeo = "chateau-talbot-chateau-talbot-connetable-de-talbot"
//    val wineSeo = "schmitt-sohne-test-this-is-label"
//    wineSeo = "penfolds-Bottega Moscato Sparkling".replace(' ','-').toLowerCase()
//    val meta = APICallFunc().fetchWineData(productId)!!
    val wineSeo = "domenico-fraccaroli-test-amarone-della-valpolicella"
    val meta = APICallFunc().fetchWineData(wineSeo)!!
    println(getJson(meta))
//    println("--------")
//    val productId = 3150.toLong()
//    val productData = APICallFunc().fetchProductData(productId)
//    println(productData!!.second.size)
//    val wineList = APICallFunc().fetchAllWine()
//    println(getJson(wineList))
}