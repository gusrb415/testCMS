package wineCMS.customPojo

class ExcelSummaryList(private val summaryList: List<ExcelSummary>) {
    fun get(day : Int) : ExcelSummary?{
        summaryList.forEach {
            if(it.day == day)
                return it
        }
        return null
    }

    fun get(day: Int, wineName: String) : ExcelSummaryWine? {
        val summary = get(day) ?: return null
        summary.wineList.forEach {
            if(it.name == wineName)
                return it
        }
        return null
    }

    fun get(day: Int, wineName: String, vintageTag: Short) : ExcelSummaryVintage? {
        val wine = get(day, wineName) ?: return null
        wine.vintageList.forEach {
            if(it.vintageTag == vintageTag)
                return it
        }
        return null
    }
}