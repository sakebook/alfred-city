import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.cinterop.*
import platform.posix.*

@Serializable
data class Item(
    val title: String,
    @SerialName("subtitle")
    val subTitle: String,
    val arg: String
)

@Serializable
data class Items(
    val items: List<Item>
)

@Serializable
data class City(
    val cityCode: String,
    val cityName: String,
    val kana: String
)

@Serializable
data class Prefecture(
    val prefectureCode: String,
    val prefectureName: String,
    val cities: List<City>
)

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        return
    }
    val query = args.get(0)
    val fileName = args.get(1)
    val jsonString = fileOpen(fileName)
    val prefectures = createPrefecture(jsonString)
    // 数字かどうかチェック
    when (query.toIntOrNull()) {
        null -> fromName(query, prefectures) // 文字列
        else -> {
            when (query.length) {
                1 -> fromPrefectureCode("0$query", prefectures) // 対象の都道府県の全部。0詰め
                2 -> fromPrefectureCode(query, prefectures) // 対象の都道府県の全部
                else -> fromCityCode(query, prefectures) // 一致する市区町村
            }
        }
    }
}

private fun fileOpen(fileName: String): String {
    val file = fopen(fileName, "r")
    if (file == null) {
        perror("cannot open file. FileName: ${fileName}")
        return ""
    }
    var jsonString: String = ""
    try {
        memScoped {
            val bufferLength = 256 * 1024
            val buffer = allocArray<ByteVar>(bufferLength)
            jsonString = fgets(buffer, bufferLength, file)?.toKString()?: return@memScoped
        }
    } finally {
        fclose(file)
    }
    return jsonString
}

private fun createPrefecture(json: String): Sequence<Prefecture> {
    val jsonList = JSON.parse(Prefecture.serializer().list, json).asSequence()
    return jsonList
}

private fun fromName(name: String, prefectures: Sequence<Prefecture>) {
    val prefecture = prefectures.firstOrNull { it.prefectureName.contains(name) }
    when (prefecture) {
        null -> {
            // マッチしなかった
            val cities = prefectures.toList().flatMap { it.cities }
            val results = cities
                .filter { it.cityName.contains(name) }
                .map {
                    Item("${it.cityName}", "${it.cityCode}", "${it.cityName} ${it.cityCode}")
                }
            val jsonData = JSON.indented.stringify(Items.serializer(), Items(results.toList()))
            println(jsonData)
        }
        else -> {
            val results = prefecture.cities.map {
                Item("${prefecture.prefectureName}${it.cityName}", "${it.cityCode}", "${it.cityName} ${it.cityCode}")
            }
            val jsonData = JSON.indented.stringify(Items.serializer(), Items(results.toList()))
            println(jsonData)
        }
    }
}

private fun fromPrefectureCode(name: String, prefectures: Sequence<Prefecture>) {
    val prefecture = prefectures.first { it.prefectureCode == name }
    val results = prefecture.cities.map {
        Item("${prefecture.prefectureName}${it.cityName}", "${it.cityCode}", "${it.cityName} ${it.cityCode}")
    }
    val jsonData = JSON.indented.stringify(Items.serializer(), Items(results.toList()))
    println(jsonData)
}

private fun fromCityCode(name: String, prefectures: Sequence<Prefecture>) {
    val prefectureCode = name.take(2)
    val prefecture = prefectures.first { it.prefectureCode.startsWith(prefectureCode) }
    val results = prefecture.cities
        .filter { it.cityCode.startsWith(name) }
        .map {
            Item("${prefecture.prefectureName}${it.cityName}", "${it.cityCode}", "${it.cityName} ${it.cityCode}")
        }
    val jsonData = JSON.indented.stringify(Items.serializer(), Items(results.toList()))
    println(jsonData)
}