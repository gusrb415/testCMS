package wineCMS.wineAPI

import com.google.gson.GsonBuilder
import de.siegmar.fastcsv.reader.CsvReader
import log
import wineCMS.customPojo.*
import wineCMS.pojo.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.io.StringReader
import java.util.ArrayList
import de.siegmar.fastcsv.writer.CsvWriter
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFRow
import org.apache.poi.xssf.usermodel.XSSFWorkbook

class Helper(readExcel: Boolean = false, excelFileName: String = "Testing Data.xlsx"){
    //Static data shared by many helper objects
    companion object {
        /**
         * This attribute names are directly input (for both zero day and static data)
         * The rest are referred to reference sheet
        */
        val directInputString = arrayOf(
                "#", "untouched", "day", "AorB", "wineSeo", "productId", "prodpckgId",
                "success", "disappear", "newWine", "newVint", "newPack"
        )

        /**
         * ProductKey -> wineSEO
         */
        val miniWineSeoDB = mutableMapOf<MiniProductKey, String>()
        /**
         * PackageKey -> Pair(Product ID, Package ID)
         */
        val miniPackageDB = mutableMapOf<MiniPackageKey, Pair<Long, Long>>()
        /**
         * wineSEO -> Pair(wine data, day of creation)
         */
        private val miniWineDB = mutableMapOf<String, Pair<VmDafdVintageMeta, Int>>()
        /**
         * ProductKey -> Pair(vintage data, day of creation)
         */
        private val miniVintageDB = mutableMapOf<MiniProductKey, Pair<VmDafdVintageMeta, Int>>()

        /**
         * API response strings for successful and rejected wines
         */
        private const val successResponse = "processed"
        private const val failedResponse = "rejected"

        /**
         * The name of excel file and the csv file names (make sure the sheet names and csv file names without extension are the same)
         */
        private const val fileName = "StaticData.csv"
        private const val refFileName = "Reference.csv"
        private const val summaryFileName = "Summary.csv"
        private const val dayZeroFileName = "DayZero.csv"
    }

    /**
     * This object variables should not be modified since they are used to call functions from those classes
     */
    private val assertCall = AssertFunc()
    private val api = APICallFunc()

    /**
     * Base path to resources folder for reading excel file and writing csv files
     */
    private val basePath = "${System.getProperty("user.dir")}/src/main/resources"

    /**
     * Comment out this line below to skip reading the excel file and overwrite on existing csv files
     */
    private val runExcelToCsv = if(readExcel) excelToCsv(excelFileName) else null

    /**
     * File data is read once when the object is initiated to reduce file i/o requests
     */
    private val fileData = readFile(fileName)
    private val refFileData = readFile(refFileName)
    private val summaryFileData = readFile(summaryFileName)
    private val dayZeroFileData = readFile(dayZeroFileName)

    /**
     * The headers of the csv files are stored in an array
     */
    private val staticAttrArr = getHeaders(fileData[0])
    private val refAttrArr = getHeaders(refFileData[0])
    private val dayZeroAttributeArr = getHeaders(dayZeroFileData[0])

    /**
     * Each header's index is stored in an array -> this allows random order of columns
     */
    private val staticIndexes = getIndexes(staticAttrArr, fileData[0])
    private val zeroIndexes = getIndexes(dayZeroAttributeArr, dayZeroFileData[0])
    private val refIndexes = getIndexes(refAttrArr, refFileData[0])

    /**
     * Each data from the csv file is read and stored in these variables (reference data is only used for reading other data)
     */
    private val refData = readRefData()
    val suppliers = readStaticData()
    val summaryData = readSummary()
    val dayZeroData = readDayZero()

    /**
     * By storing day list from smallest to highest, it allows days not to be continuous numbers
     */
    val dayList = getDayListInAllSuppliers()

    ///////////////////////////////////////////////////PUBLIC FUNCTIONS///////////////////////////////////////////////////
    /**
     * Using the response (either success or failure), after it finds the same wine using the package key (wineRefEXT, vintage, package type etc.)
     *      on success, it updates the wineSeo, productId, prodpckgId of the wine
     *      on failure, it updates the errMsg of the wine
     * @param result response received from process update in Map<ResponseString, ListOfWinesProcessed>
     * @param suppliers the full list of suppliers read from excel file
     * @param day the day of this update
     * @param supplierID the ID of this supplier
     * @return TRUE for successful process (Only exception is thrown if anything fails since there is no point continuation of the process)
     */
    fun processResultA(result: Map<String, List<VmDafdPdpkVinImport>>, suppliers: List<ExcelSupplier>, day: Int, supplierID: String): Boolean {
        for (supplier in suppliers) {
            if (supplier.supplierID == supplierID && supplier.day == day) {
                for (wine in supplier.wineList) {
                    val miniPackageKey = MiniPackageKey(wine.vinImport.pdpk)
                    val miniProductKey = miniPackageKey.productKey

                    if (result[successResponse]!!.isNotEmpty()) {
                        for (it in result[successResponse]!!) {
                            val resMiniPackageKey = MiniPackageKey(it.pdpk)
                            if (miniPackageKey == resMiniPackageKey && wine.aOrB == "A") {
                                if (!miniWineSeoDB.contains(miniProductKey)) {
                                    var wineSeo = ""
                                    miniWineSeoDB.forEach {
                                        if (it.key.wineRefEXT == miniProductKey.wineRefEXT)
                                            wineSeo = it.value
                                    }
                                    miniWineSeoDB[miniProductKey] = wineSeo
                                }
                                wine.vinImport.wineSeo = miniWineSeoDB[miniProductKey]
                                wine.vinImport.productId = it.productId!!
                                wine.vinImport.prodpckgId = it.prodpckgId!!
                                miniPackageDB[miniPackageKey] = it.productId!! to it.prodpckgId!!
                                updateDatabase(wine, it, day)
                                break
                            }
                        }
                    }
                    if (result[failedResponse]!!.isNotEmpty()) {
                        for (it in result[failedResponse]!!) {
                            val resMiniPackageKey = MiniPackageKey(it.pdpk)

                            if (miniPackageKey == resMiniPackageKey && wine.aOrB == "A") {
                                wine.vinImport.errMsg = it.errMsg!!
                                break
                            }
                        }
                    }
                }
            }
        }
        return true
    }

    /**
     * Using the response (either success or failure), after it finds the same wine using the package key (wineRefEXT, vintage, package type etc.)
     *      on success, it updates the wineSeo, productId, prodpckgId of the wine
     *      on failure, it updates the errMsg of the wine
     * @param result response received from process approve in Map<ResponseString, Map<SupplierSeo, ListOfWines>>
     * @param suppliers the full list of suppliers read from excel file
     * @param day the day of this update
     * @return TRUE is returned for successful process (Only exception is thrown if anything fails since there is no point continuation of the process)
     */
    fun processResultB(result: Map<String, Map<String, List<VmDafdPdpkVinImport>>>, suppliers: List<ExcelSupplier>, day: Int): Boolean {
        for (supplier in suppliers) {
            if (supplier.day == day) {
                val supplierID = supplier.supplierID
                for (wine in supplier.wineList) {
                    val miniPackageKey = MiniPackageKey(wine.vinImport.pdpk)
                    val miniProductKey = miniPackageKey.productKey

                    if (result[successResponse]!![supplierID] != null) {
                        for (it in result[successResponse]!![supplierID]!!) {
                            val resMiniPackageKey = MiniPackageKey(it.pdpk)
                            if (miniPackageKey == resMiniPackageKey && wine.aOrB == "B") {
                                if (it.wineSeo != null && !wine.newWine) {
                                    if(wine.vinImport.wineSeo == null) {
                                        log.logAppend("Index ${wine.index}: It is a new wine")
                                    }
                                    wine.newWine = true
                                }
                                if (wine.newWine) {
                                    val wineSeo = it.wineSeo
                                    if (wineSeo == null) {
                                        log.logAppend("Index ${wine.index}: It is not a new wine")
                                    } else {
                                        wine.vinImport.wineSeo = wineSeo
                                        miniWineSeoDB[miniProductKey] = wineSeo
                                        miniWineDB[wineSeo] = it.vin!! to day
                                        miniVintageDB[miniProductKey] = it.vin to day
                                    }
                                } else
                                    updateDatabase(wine, it, day)

                                wine.vinImport.productId = it.productId
                                wine.vinImport.prodpckgId = it.prodpckgId
                                miniPackageDB[miniPackageKey] = it.productId!! to it.prodpckgId!!
                                break
                            }
                        }
                    }

                    if (result[failedResponse]!![supplierID] != null) {
                        for (it in result[failedResponse]!![supplierID]!!) {
                            val resMiniPackageKey = MiniPackageKey(it.pdpk)

                            if (miniPackageKey == resMiniPackageKey && wine.aOrB == "B") {
                                wine.vinImport.errMsg = it.errMsg!!
                                break
                            }
                        }
                    }
                }
            }
        }
        return true
    }

    /**
     * This function is used to export a json file to be uploaded for calling update APIs
     * @param any any object to be exported in json file
     * @param day day of this wines
     * @param supplierSeo supplierSeo is used to create json file under its name
     * @return path of the file exported
     */
    fun outputFile(any: Any?, day: Int, supplierSeo: String): String {
        val basePath = "$basePath/jsonOutput/day-$day/processUpdate"
        if (!File(basePath).exists())
            Files.createDirectories(Paths.get(basePath))
        return outputFile(any, "$basePath/$supplierSeo.json")
    }

    /**
     * This function is used to export a json file to be uploaded for calling approve APIs
     * @param any any object to be exported in json file
     * @param day day of this wines
     * @return path of the file exported
     */
    fun outputFile(any: Any?, day: Int): String {
        val basePath = "$basePath/jsonOutput/day-$day/processApprove"
        if (!File(basePath).exists())
            Files.createDirectories(Paths.get(basePath))
        var index = 1
        while (File("$basePath/$index.json").exists())
            index++
        return outputFile(any, "$basePath/$index.json")
    }

    /**
     * It receives any object and turn it into Json String using Gson library
     * (unless it is used to see the response, it should be private since it is used only for exporting files)
     * @param any any object
     * @return json string
     */
    fun getJson(any: Any?): String {
        return GsonBuilder().setPrettyPrinting().create().toJson(any)
    }

    /**
     * It is a summary of checks after update and approve process.
     * Each line of excel wine data is supposed to be checked on pacakge data, vintage data, wine data, product data, final count etc.
     * @param wine wine data of the excel wine data
     * @param supplierID supplierID of this wine
     * @param day day of this run to make sure it checks on correct day wine data
     * @param summary for comparison of counts (wine, vintage, package)
     * @param initialCount the count of all wines before the API calls
     * @return If any of the test fails, it returns false after all checks. If all tests succeed, then true is returned
     */
    fun checkCommonFactors(wine: ExcelFinalWine, supplierID: String, day: Int, summary: ExcelSummaryList, initialCount: Int): Boolean {
        val vintage = wine.vinImport.pdpk.vintageTag!!
        val wineSEO = wine.vinImport.wineSeo!!
        var check = true
        if (!assertCall.packageLevelComparison(supplierID, wine))
            check = false

        val wineRefEXT = wine.vinImport.pdpk.wineRefEXT!!
        val key = MiniProductKey(wineRefEXT, vintage)
        val vintageData = miniVintageDB[key]!!.first
        if (!assertCall.vintageLevelComparison(wine, wineSEO, vintageData))
            check = false

        val label = wine.vinImport.vin?.label
        val name = "${wine.vinImport.vin?.winery} ${if(label == null) "" else "$label "}"
        val productName = "$name${if (vintage == 1001.toShort()) "N.V." else vintage.toString()}"

        val expectedProduct = ProductHolder(wine.vinImport.productId, productName)
        val resultProduct = api.fetchProductData(wine.vinImport.productId!!)?.first!!
        if (!assertCall.productLevelAttributeAssert(expectedProduct, resultProduct))
            check = false

        val wineDataExpected = miniWineDB[wineSEO]!!.first
        val wineDataResult = api.fetchWineData(wineSEO)!![vintage]
                ?: api.fetchWineData(wine.vinImport.productId!!)!![vintage]!!
        if (!assertCall.wineLevelAttributeAssert(wineDataExpected, wineDataResult))
            check = false

        val productID = wine.vinImport.productId!!
        if (!assertCall.checkVintagePackageCount(summary, day, name, vintage, wineSEO, productID))
            check = false

        if (!assertCall.checkSoldOut(wine))
            check = false

        val expected = initialCount + (summary.get(day)?.newWineCount ?: 0)
        val comparison = assertCall.wineCountCheck(expected)
        if (!comparison)
            check = false

        return check
    }

    /**
     * It goes through all suppliers to get a supplier's wine list to be used for process update
     * @param suppliers list of all supplier data from excel file
     * @param supplierID the supplier's ID
     * @param day the day of this run
     * @return list of all wines summarized (if empty, null is returned)
     */
    fun getImportAList(suppliers: List<ExcelSupplier>, supplierID: String, day: Int): List<VmDafdPdpkVinImport>? {
        val list = mutableListOf<VmDafdPdpkVinImport>()
        suppliers.forEach {
            if (it.day == day && it.supplierID == supplierID) {
                for (wine in it.wineList) {
                    if (wine.aOrB == "A" && !wine.disappear) {
                        list.add(wine.vinImport)
                    }
                }
            }
        }
        if (list.size == 0)
            return null
        return list
    }

    /**
     * It goes through all suppliers to get all supplier's wine list in this day to be used for process approve
     * @param suppliers list of all supplier data from excel file
     * @param day the day of this run
     * @return map of supplier ID to list of all wines summarized (if empty, null is returned)
     */
    fun getImportBMap(suppliers: List<ExcelSupplier>, day: Int): Map<String, List<VmDafdPdpkVinImport>>? {
        val map = mutableMapOf<String, MutableList<VmDafdPdpkVinImport>>()
        suppliers.forEach {
            if (it.day == day) {
                for (wine in it.wineList) {
                    if (wine.aOrB == "B" && !wine.disappear) {
                        if (map.contains(it.supplierID)) {
                            map[it.supplierID]!!.add(wine.vinImport)
                        } else {
                            map[it.supplierID] = mutableListOf(wine.vinImport)
                        }
                    }
                }
            }
        }
        if (map.isEmpty())
            return null
        return map
    }

    /**
     * If any of supplier would run process update on this day, true is returned
     * (This is to make sure it stops the process update call if there is no wine to be updated this day)
     * @param day the day of this run
     * @param suppliers the list of all suppliers
     * @return if there is any wine to run update, true is returned
     */
    fun checkNotEmptyList(day: Int, suppliers: List<ExcelSupplier>): Boolean {
        suppliers.forEach {
            if (it.day == day)
                for (wine in it.wineList)
                    if (wine.aOrB == "A")
                        return true
        }
        return false
    }

    ///////////////////////////////////////////////////PRIVATE FUNCTIONS///////////////////////////////////////////////////
    /**
     * It turns any object received into json string then export the file to specific path or just general path
     * @param any Any object to be converted
     * @param path If this object is to be exported to specific path for update or approve process else it is null
     * @return path of the exported file (if it fails in file export, it returns empty string)
     */
    fun outputFile(any: Any?, path: String? = null): String {
        val json = getJson(any)

        val outputPath = if (path == null) {
            val basePath = "$basePath/jsonOutput"
            if (!File(basePath).exists())
                Files.createDirectories(Paths.get(basePath))
            var index = 1
            while (File("$basePath/any$index.json").exists())
                index++
            val fileName = "any$index.json"
            "$basePath/$fileName"
        } else {
            path
        }

        return try {
            if (!File(outputPath).exists())
                Files.write(Paths.get(outputPath), json.toByteArray(), StandardOpenOption.CREATE)
            outputPath
        } catch (e: IOException) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * It reads the excel file and the first 4 worksheets are exported in csv format
     * @param excelFileName the file name of the excel file
     */
    private fun excelToCsv(excelFileName: String) {
        if (!File(basePath).exists())
            Files.createDirectories(Paths.get(basePath))
        val myWorkBook = XSSFWorkbook(File("$basePath/$excelFileName"))

        for (i in 0 until Math.min(4, myWorkBook.numberOfSheets)) {
            val mySheet = myWorkBook.getSheetAt(i)
            val sheetName = myWorkBook.getSheetName(i)

            val rowIterator = mySheet.rowIterator()
            val rowData = mutableListOf<Array<String>>()
            var firstRowSize = 0
            while (rowIterator.hasNext()) {
                val myRow = rowIterator.next() as XSSFRow
                if (sheetName != "Summary")
                    myRow.getCell(0)?.rawValue ?: break

                val csvData = mutableListOf<String>()

                firstRowSize = Math.max(firstRowSize, myRow.count())
                var checkAllEmpty = true
                for (j in 0 until firstRowSize) {
                    val cell = myRow.getCell(j)
                    csvData.add(
                            when (cell?.cellTypeEnum) {
                                CellType.BOOLEAN -> {
                                    checkAllEmpty = false
                                    cell.booleanCellValue.toString().toUpperCase()
                                }
                                CellType.NUMERIC -> {
                                    val decimal = cell.numericCellValue.toString().toBigDecimal()
                                    val int = decimal.toInt()
                                    checkAllEmpty = false
                                    if (decimal - int.toBigDecimal() < 0.00001.toBigDecimal())
                                        int.toString()
                                    else
                                        decimal.toString()
                                }
                                CellType.STRING -> {
                                    checkAllEmpty = false
                                    cell.stringCellValue
                                }
                                CellType.FORMULA -> {
                                    checkAllEmpty = false
                                    cell.rawValue
                                }
                                else -> ""
                            }
                    )
                }
                if (checkAllEmpty)
                    break
                rowData.add(csvData.toTypedArray())
            }
            writeToCsv("$basePath/$sheetName.csv", rowData)
        }
    }

    /**
     * It write the list of strings in csv format into path specified
     * @param csvFilePath csv file path
     * @param list list of csv strings
     */
    private fun writeToCsv(csvFilePath: String, list: List<Array<String>>) {
        val file = File(csvFilePath)
        file.delete()
        val csvWriter = CsvWriter()
        val data = ArrayList<Array<String>>()
        for (array in list) {
            if (array.isEmpty())
                break
            data.add(array)
        }

        csvWriter.write(file, Charsets.UTF_8, data)
    }

    /**
     * It reads csv header string and return the headers in array
     * @param str csv header string
     * @return headers in array
     */
    private fun getHeaders(str: String): Array<String> {
        val arr = csvSplit(str)
        val list = mutableListOf<String>()
        arr.forEach {
            if (it.toUpperCase() != "PREVIEW" || it == "")
                list.add(it)
        }
        return list.toTypedArray()
    }

    /**
     * It reads all suppliers data and returns the day list from smallest to highest
     * @return the sorted day list
     */
    private fun getDayListInAllSuppliers(): List<Int> {
        val dayList = mutableListOf<Int>()
        suppliers.forEach {
            var check = false
            for (day in dayList) {
                if (it.day == day)
                    check = true
            }
            if (!check)
                dayList.add(it.day)
        }
        dayList.sort()
        return dayList
    }

    /**
     * It is used to process vintage level database management after update and approve process
     * @param wine the wine of to be processed
     * @param result the result of this same wine
     * @day to store when the vintage data is created if it is first vintage data created
     */
    private fun updateDatabase(wine: ExcelFinalWine, result: VmDafdPdpkVinImport, day: Int) {
        val packageKey = MiniPackageKey(wine.vinImport.pdpk)
        val productKey = packageKey.productKey
        if (wine.newWine) {
            if (miniWineSeoDB.contains(productKey))
                log.logAppend("Index ${wine.index}: This is not a new wine")
        }

        if (result.vin != null && !wine.newVint) {
            if(wine.aOrB != "B")
                log.logAppend("Index ${wine.index}: This is a new vintage")
            wine.newVint = true
        }

        var wineSeo: String? = null
        miniWineSeoDB.keys.forEach {
            if (it.wineRefEXT == productKey.wineRefEXT)
                wineSeo = miniWineSeoDB[it]!!
        }
        miniWineSeoDB[productKey] = wineSeo!!
        wine.vinImport.wineSeo = wineSeo

        if (wine.newVint) {
            if (miniVintageDB.containsKey(productKey))
                log.logAppend("Index ${wine.index}: This is not a new vintage")
            else
                miniVintageDB[productKey] = Pair(wine.vinImport.vin!!, day)
        }

        if (!miniVintageDB.contains(productKey)) {
            miniVintageDB[productKey] = api.fetchWineData(wineSeo!!, wine.vinImport.pdpk.vintageTag!!)!! to -1
        }
    }

    /**
     * It uses fastCsv library to parse a csv string into list of strings
     * @param str csv string
     * @return list of strings
     */
    private fun csvSplit(str: String): List<String> {
        val csvParser = CsvReader().parse(StringReader(str))
        return csvParser.nextRow().fields
    }

    /**
     * It returns the exact indexes of each attribute in the csv file
     * @param attrNames array of attributes
     * @param data csv string of headers
     * @return integer array containing indexes of each attribute (same index as attributes)
     */
    private fun getIndexes(attrNames: Array<String>, data: String): IntArray {
        val intArray = IntArray(attrNames.size) { _ -> -1 }

        val splitArr = csvSplit(data)
        for (i in 0 until attrNames.size) {
            for (j in 0 until splitArr.size) {
                if (attrNames[i].toLowerCase() == splitArr[j].toLowerCase()) {
                    intArray[i] = j
                    break
                }
            }
        }

        return intArray
    }

    /**
     * It gets the indexes of varietals/ratings to be recorded and return that varietals/ratings
     * @param stringList indexes of varietals/ratings in string
     * @param objectList list of all varietals/ratings
     * @return list of varietals/ratings according to list
     */
    private fun <T> filter(stringList: List<String>, objectList: List<T>): List<T>? {
        val tempList = mutableListOf<T>()
        for (i in stringList) {
            try{
                tempList.add(objectList[i.toInt()])
            }catch(e: NumberFormatException){
                continue
            }
        }
        return if (tempList.isEmpty()) null else tempList
    }

    /**
     * Read a file data to only get all the ratings and varietals
     * @param varietalIndex index of the varietalList header in the reference file
     * @param ratingIndex index of the ratingList header in the reference file
     * @return pair of varietal list and rating list
     */
    private fun getVarietalsAndRatings(varietalIndex: Int, ratingIndex: Int, fileData: Array<String>): Pair<List<VmDafdWineVarietal>, List<VmDafdWineRating>> {
        val varietalList = mutableListOf<VmDafdWineVarietal>()
        val ratingList = mutableListOf<VmDafdWineRating>()
        varietalList.add(VmDafdWineVarietal())
        ratingList.add(VmDafdWineRating())
        for (it in fileData) {
            val splitArr = csvSplit(it)
            try {
                splitArr[0].toInt()
            } catch (e: NumberFormatException) {
                continue
            }

            if (splitArr[ratingIndex + 1] != "")
                ratingList.add(VmDafdWineRating(splitArr[ratingIndex], null, splitArr[ratingIndex + 1]))

            if (splitArr[varietalIndex + 1] != "") {
                val numVal = if (splitArr[varietalIndex + 1] == "") null else splitArr[varietalIndex + 1].toShort()
                varietalList.add(VmDafdWineVarietal(splitArr[varietalIndex], null, numVal))
            }
        }
        return varietalList to ratingList
    }

    /**
     * Read a file and return all the lines in array of strings
     * @param fileName file name in the main/resources folder
     * @return array of strings read
     */
    private fun readFile(fileName: String): Array<String> {
        val path = "$basePath/$fileName"
        return File(path).readLines(Charsets.UTF_8).toTypedArray()
    }

    /**
     * Read reference file and return all the data read in array of list of strings (all data in string)
     * @return array of list of strings (each cell in excel file as one string object)
     */
    private fun readRefData(): Array<List<String>> {
        val listArr = Array(refIndexes.size) { _ -> mutableListOf<String>() }

        for (list in listArr)
            list.add("")

        refFileData.forEach {
            val arr = csvSplit(it)
            try {
                arr[0].toInt()
            } catch (e: Exception) {
                return@forEach
            }
            for (i in 1 until refIndexes.size) {
                listArr[i].add(arr[refIndexes[i]])
            }
        }
        listArr[0] = mutableListOf("")
        return Array(refIndexes.size) { i -> listArr[i] }
    }

    /**
     * Read the summary csv file and returns the summary list object
     * @return excel summary list object that contains all days summary information
     */
    private fun readSummary(): ExcelSummaryList {
        val data = summaryFileData
        val summaryList = mutableListOf<ExcelSummary>()
        var wineList = mutableListOf<ExcelSummaryWine>()
        var day = -1
        var newWineCount = -1
        val lastRow = data.size - if (data.size % 2 == 1) 1 else 2
        for (line in 1 until lastRow step 2) {
            val vintageList = mutableListOf<ExcelSummaryVintage>()
            val splitData1 = csvSplit(data[line])
            val splitData2 = csvSplit(data[line + 1])
            if (splitData1[0] != "" && wineList.isNotEmpty()) {
                summaryList.add(ExcelSummary(day, wineList, newWineCount))
                wineList = mutableListOf()
                day = splitData1[0].toInt()
                newWineCount = splitData1[1].toInt()
            } else if (splitData1[0] != "" && wineList.isEmpty()) {
                day = splitData1[0].toInt()
                newWineCount = splitData1[1].toInt()
            }

            for (i in 5 until splitData1.size) {
                if (splitData1[i] != "" && splitData2[i] != "") {
                    vintageList.add(ExcelSummaryVintage(splitData1[i].toShort(), splitData2[i].toInt()))
                }
            }

            val winery = refData[refIndexes[refAttrArr.indexOf("winery")]][splitData1[2].toInt()]
            val label = refData[refIndexes[refAttrArr.indexOf("label")]][splitData1[2].toInt()]
            val wineName = "$winery ${if(label == "") "" else "$label "}"
            if (vintageList.isNotEmpty()) {
                wineList.add(ExcelSummaryWine(wineName, vintageList, splitData1[3].toInt()))
            }
        }
        if (wineList.isNotEmpty())
            summaryList.add(ExcelSummary(day, wineList, newWineCount))
        return ExcelSummaryList(summaryList)
    }

    /**
     * Read zero day data and return List<Pair<Untouched, Supplier>>
     * @return list of pairs of untouched/touched (true/false) and supplier data with winelists
     */
    private fun readDayZero(): List<Pair<Boolean, ExcelSupplier>>? {
        val supplierList = mutableListOf<Pair<Boolean, ExcelSupplier>>()
        var wineList = mutableListOf<ExcelFinalWine>()
        var supplierId = ""
        var untouched = true

        dayZeroFileData.forEach {
            val arr = csvSplit(it)
            try {
                arr[0].toInt()
            } catch (e: Exception) {
                return@forEach
            }

            val index = arr[0].toInt()
            val supplierId2 = refData[refIndexes[refAttrArr.indexOf("supplierSeo")]][arr[zeroIndexes[dayZeroAttributeArr.indexOf("supplierSeo")]].toInt()]
            val untouched2 = arr[zeroIndexes[dayZeroAttributeArr.indexOf("untouched")]].toUpperCase()
            if ((supplierId != supplierId2 && supplierId != "") || (untouched != (untouched2 == "TRUE"))) {
                if (wineList.isNotEmpty()) {
                    supplierList.add(Pair(untouched, ExcelSupplier(wineList, supplierId, 0)))
                    wineList = mutableListOf()
                }
            }
            if (arr[zeroIndexes[dayZeroAttributeArr.indexOf("supplierSeo")]] != "")
                supplierId = refData[refIndexes[refAttrArr.indexOf("supplierSeo")]][arr[zeroIndexes[dayZeroAttributeArr.indexOf("supplierSeo")]].toInt()]
            if (arr[zeroIndexes[dayZeroAttributeArr.indexOf("untouched")]] != "")
                untouched = arr[zeroIndexes[dayZeroAttributeArr.indexOf("untouched")]].toUpperCase() == "TRUE"

            try {
                if(arr[staticIndexes[dayZeroAttributeArr.indexOf("wineSeo")]] == "") throw Exception("missing wineSeo")
                arr[staticIndexes[dayZeroAttributeArr.indexOf("productId")]].toLong()
                arr[staticIndexes[dayZeroAttributeArr.indexOf("prodpckgId")]].toLong()
            }catch(e: Exception) {
                return@forEach
            }

            val wineSEO = arr[staticIndexes[dayZeroAttributeArr.indexOf("wineSeo")]]
            val productId = arr[staticIndexes[dayZeroAttributeArr.indexOf("productId")]].toLong()
            val prodpckgId = arr[staticIndexes[dayZeroAttributeArr.indexOf("prodpckgId")]].toLong()

            val wineRefEXT = if (arr[zeroIndexes[dayZeroAttributeArr.indexOf("wineRefEXT")]] == "") null else refData[refIndexes[refAttrArr.indexOf("wineRefEXT")]][arr[zeroIndexes[dayZeroAttributeArr.indexOf("wineRefEXT")]].toInt()]
            val vintageTag = refData[refIndexes[refAttrArr.indexOf("vintageTag")]][arr[zeroIndexes[dayZeroAttributeArr.indexOf("vintageTag")]].toInt()]
            val pckgType = if (arr[zeroIndexes[dayZeroAttributeArr.indexOf("pckgType")]] == "") null else refData[refIndexes[refAttrArr.indexOf("pckgType")]][arr[zeroIndexes[dayZeroAttributeArr.indexOf("pckgType")]].toInt()]
            val pckgRefEXT = if (arr[zeroIndexes[dayZeroAttributeArr.indexOf("pckgRefEXT")]] == "") null else refData[refIndexes[refAttrArr.indexOf("pckgRefEXT")]][zeroIndexes[dayZeroAttributeArr.indexOf("pckgRefEXT")]]

            if (vintageTag == "") throw Exception("FILL UP VINTAGE TAG IN ZERO DAY")
            val wineData = APICallFunc().fetchWineData(wineSEO, vintageTag.toShort())!!
            val packageData = APICallFunc().fetchProductData(productId, prodpckgId)!!
            packageData.pdpk.vintageTag = vintageTag.toShort()
            val vinImport = VmDafdPdpkVinImport(packageData.pdpk, wineData, wineSEO, productId, prodpckgId)

            wineList.add(ExcelFinalWine(vinImport, index, "A", true, null, false, false, false, false))

            if (wineRefEXT != null && pckgType != null) {
                val productKey = MiniProductKey("${wineRefEXT}_${if (supplierId == "moet-hennessy-diageo") "supplier001" else supplierId}", vintageTag.toShort())
                val packageKey = MiniPackageKey(productKey, pckgType, pckgRefEXT)

                if (!miniWineSeoDB.contains(productKey)) {
                    miniWineSeoDB[productKey] = wineSEO
                    miniWineDB[wineSEO] = Pair(wineData, 0)
                }

                if (!miniVintageDB.contains(productKey)) {
                    miniVintageDB[productKey] = Pair(wineData, 0)
                }

                if (!miniPackageDB.contains(packageKey)) {
                    miniPackageDB[packageKey] = Pair(productId, prodpckgId)
                }
            }
        }
        if (wineList.isNotEmpty())
            supplierList.add(Pair(untouched, ExcelSupplier(wineList.toList(), supplierId, 0)))
        if (supplierList.isEmpty())
            return null
        return supplierList
    }

    /**
     * Read the static data file and return list of suppliers
     * @return list of suppliers (each supplier containing supplier ID, day, wine list)
     */
    private fun readStaticData(): List<ExcelSupplier> {
        val data = fileData
        val indexes = staticIndexes
        val refIndexes = refIndexes
        val varietalAndRatings = getVarietalsAndRatings(refIndexes[refAttrArr.indexOf("varietalSeo")], refIndexes[refAttrArr.indexOf("criticsSeo")], refFileData)
        val ratings = varietalAndRatings.second
        val varietals = varietalAndRatings.first

        val supplierList = mutableListOf<ExcelSupplier>()
        var wineList = mutableListOf<ExcelFinalWine>()
        var day = ""
        var supplierId = ""
        var aORb = ""
        data.forEach {
            val arr = csvSplit(it)
            try {
                arr[0].toInt()
            } catch (e: Exception) {
                return@forEach
            }

            val index = arr[0].toInt()
            val attrValueMap = mutableMapOf<String, String>()
            for (i in 0 until staticAttrArr.size) {
                attrValueMap[staticAttrArr[i]] = arr[indexes[staticAttrArr.indexOf(staticAttrArr[i])]]
            }

            val checkMap = mutableMapOf<String, Boolean>()
            for (attribute in staticAttrArr)
                checkMap[attribute] = (attrValueMap[attribute] != "")

            val directInputCheck = mutableMapOf<String, Boolean>()
            for (str in directInputString)
                directInputCheck[str] = true

            val dataMap = mutableMapOf<String, String?>()
            for (attribute in staticAttrArr) {
                try {
                    dataMap[attribute] = if (!checkMap[attribute]!!) null else {
                        if (directInputCheck[attribute] == true) {
                            attrValueMap[attribute]
                        } else {
                            if(refData[refIndexes[refAttrArr.indexOf(attribute)]][attrValueMap[attribute]!!.toInt()] == "")
                                null
                            else
                                refData[refIndexes[refAttrArr.indexOf(attribute)]][attrValueMap[attribute]!!.toInt()]
                        }
                    }
                } catch (e: Exception) {
                    throw Exception("missing $attribute")
                }
            }

            val check1 = (dataMap["day"] != day && dataMap["day"] != null)
            val check2 = (dataMap["supplierSeo"] != supplierId && dataMap["supplierSeo"] != null)

            if (check1 || check2) {
                if (wineList.isNotEmpty()) {
                    supplierList.add(ExcelSupplier(wineList.toList(), supplierId, day.toInt()))
                    wineList = mutableListOf()
                }
            }

            if (dataMap["supplierSeo"] != null) supplierId = dataMap["supplierSeo"].toString()
            if (dataMap["day"] != null) day = dataMap["day"].toString()
            if (dataMap["AorB"] != null) aORb = dataMap["AorB"].toString()
            val tempRating = if (dataMap["ratingList"] != null) filter(dataMap["ratingList"].toString().split(","), ratings) else null
            val tempVarietal = if (dataMap["varietalList"] != null) filter(dataMap["varietalList"].toString().split(","), varietals) else null

            val tempPdpk = VmDafdPdpkPriceStock(
                    wineRefEXT = "${dataMap["wineRefEXT"]}_${if (supplierId == "moet-hennessy-diageo") "supplier001" else supplierId}",
                    vintageTag = dataMap["vintageTag"]?.toShort(), pckgType = dataMap["pckgType"],
                    pckgRefEXT = dataMap["pckgRefEXT"], pckgName = dataMap["pckgName"],
                    pckgNameChs = if (attrValueMap["pckgName"] == "") null else if (refData[refIndexes[refAttrArr.indexOf("pckgNameChs")]][attrValueMap["pckgName"]!!.toInt()] == "") null else refData[refIndexes[refAttrArr.indexOf("pckgNameChs")]][attrValueMap["pckgName"]!!.toInt()],
                    pckgNameCht = if (attrValueMap["pckgName"] == "") null else if (refData[refIndexes[refAttrArr.indexOf("pckgNameCht")]][attrValueMap["pckgName"]!!.toInt()] == "") null else refData[refIndexes[refAttrArr.indexOf("pckgNameCht")]][attrValueMap["pckgName"]!!.toInt()],
                    hrDlvMax = dataMap["hrDlvMax"]?.toLong(), hrDlvMin = dataMap["hrDlvMin"]?.toLong(),
                    qty = dataMap["qty"]?.toLong(), price = dataMap["price"]?.toBigDecimal(),
                    pckgImgUrl = dataMap["pckgImgUrl"]
            )

            val winerySeoIndex = attrValueMap["winerySeo"]?.toInt() ?: 0
            val labelIndex = attrValueMap["label"]?.toInt() ?: 0
            val vinNoteIndex = attrValueMap["vinNote"]?.toInt() ?: 0
            val tempWineInfo = VmDafdVintageMeta(
                    typeSeo = dataMap["typeSeo"], winerySeo = dataMap["winerySeo"],
                    winery = if (refData[refIndexes[refAttrArr.indexOf("winery")]][winerySeoIndex] != "") refData[refIndexes[refAttrArr.indexOf("winery")]][winerySeoIndex] else null,
                    wineryCht = if (refData[refIndexes[refAttrArr.indexOf("wineryCht")]][winerySeoIndex] != "") refData[refIndexes[refAttrArr.indexOf("wineryCht")]][winerySeoIndex] else null,
                    wineryChs = if (refData[refIndexes[refAttrArr.indexOf("wineryChs")]][winerySeoIndex] != "") refData[refIndexes[refAttrArr.indexOf("wineryChs")]][winerySeoIndex] else null,
                    wineryNote = if (refData[refIndexes[refAttrArr.indexOf("wineryNote")]][winerySeoIndex] != "") refData[refIndexes[refAttrArr.indexOf("wineryNote")]][winerySeoIndex] else null,
                    wineryNoteCht = if (refData[refIndexes[refAttrArr.indexOf("wineryNoteCht")]][winerySeoIndex] != "") refData[refIndexes[refAttrArr.indexOf("wineryNoteCht")]][winerySeoIndex] else null,
                    wineryNoteChs = if (refData[refIndexes[refAttrArr.indexOf("wineryNoteChs")]][winerySeoIndex] != "") refData[refIndexes[refAttrArr.indexOf("wineryNoteChs")]][winerySeoIndex] else null,
                    label = dataMap["label"],
                    labelCht = if (refData[refIndexes[refAttrArr.indexOf("labelCht")]][labelIndex] != "") refData[refIndexes[refAttrArr.indexOf("labelCht")]][labelIndex] else null,
                    labelChs = if (refData[refIndexes[refAttrArr.indexOf("labelChs")]][labelIndex] != "") refData[refIndexes[refAttrArr.indexOf("labelChs")]][labelIndex] else null,
                    regionSeo = dataMap["regionSeo"], region = dataMap["region"], countrySeo = dataMap["countrySeo"],
                    vinNote = dataMap["vinNote"],
                    vinNoteCht = if (refData[refIndexes[refAttrArr.indexOf("vinNoteCht")]][vinNoteIndex] != "") refData[refIndexes[refAttrArr.indexOf("vinNoteCht")]][vinNoteIndex] else null,
                    vinNoteChs = if (refData[refIndexes[refAttrArr.indexOf("vinNoteChs")]][vinNoteIndex] != "") refData[refIndexes[refAttrArr.indexOf("vinNoteChs")]][vinNoteIndex] else null,
                    vinImgUrl = dataMap["vinImgUrl"], alcohol = dataMap["alcohol"]?.toShort(), appellation = dataMap["appellation"],
                    ratings = tempRating, varietals = tempVarietal
            )

            val success = dataMap["success"]?.toUpperCase() == "TRUE"
            val newWine = dataMap["newWine"]?.toUpperCase() == "TRUE"
            val newVint = dataMap["newVint"]?.toUpperCase() == "TRUE"
            val newPack = dataMap["newPack"]?.toUpperCase() == "TRUE"
            val disappear = dataMap["disappear"]?.toUpperCase() == "TRUE"
            val vinImport = VmDafdPdpkVinImport(tempPdpk, tempWineInfo, dataMap["wineSeo"], dataMap["productId"]?.toLong(), dataMap["prodpckgId"]?.toLong())

            wineList.add(ExcelFinalWine(vinImport, index, aORb, success, dataMap["reason"], disappear, newWine, newVint, newPack))
        }

        if (wineList.isNotEmpty())
            supplierList.add(ExcelSupplier(wineList.toList(), supplierId, day.toInt()))

        return supplierList.toList()
    }
}