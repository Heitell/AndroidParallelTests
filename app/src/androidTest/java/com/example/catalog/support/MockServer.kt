package com.example.catalog.support

import com.example.catalog.data.AppGraph
import com.example.catalog.data.Item
import com.example.catalog.data.ItemDetails
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import org.junit.rules.ExternalResource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Заглушка сервера прямо на устройстве. Своя на каждый тест: свой порт, свои ответы.
 * Ответы выбираются по пути запроса, поэтому не зависят от порядка запросов.
 */
class MockServerRule(private val timeoutMs: Long = 20_000) : ExternalResource() {
    private val server = MockWebServer()
    val routes = Routes()

    override fun before() {
        server.dispatcher = routes
        server.start()
        AppGraph.configure(server.url("/api/").toString(), timeoutMs)
    }

    override fun after() {
        server.close()
    }
}

typealias Handler = (request: RecordedRequest, call: Int) -> MockResponse

class Routes : Dispatcher() {
    private val handlers = ConcurrentHashMap<String, Handler>()
    private val calls = ConcurrentHashMap<String, AtomicInteger>()
    val requests = CopyOnWriteArrayList<RecordedRequest>()

    /** [call] — номер обращения к этому пути, с нуля. */
    fun on(path: String, handler: Handler) {
        handlers["/api/$path"] = handler
    }

    fun callsTo(path: String): Int = requests.count { it.url.encodedPath == "/api/$path" }

    override fun dispatch(request: RecordedRequest): MockResponse {
        requests += request
        val path = request.url.encodedPath
        val call = calls.getOrPut(path) { AtomicInteger() }.getAndIncrement()
        return handlers[path]?.invoke(request, call) ?: MockResponse(code = 404)
    }
}

object Responses {
    private val json = Json

    fun items(items: List<Item>, delayMs: Long = Net.FAST) = ok(json.encodeToString(items), delayMs)

    fun details(item: ItemDetails, delayMs: Long = Net.FAST) = ok(json.encodeToString(item), delayMs)

    fun ok(body: String, delayMs: Long) = MockResponse.Builder()
        .code(200)
        .setHeader("Content-Type", "application/json")
        .body(body)
        .bodyDelay(delayMs, TimeUnit.MILLISECONDS)
        .build()

    fun error(code: Int, delayMs: Long = Net.FAST) = MockResponse.Builder()
        .code(code)
        .body("""{"error":"boom"}""")
        .bodyDelay(delayMs, TimeUnit.MILLISECONDS)
        .build()

    /** Сервер закрывает соединение, не ответив. */
    fun disconnect() = MockResponse.Builder()
        .onResponseStart(SocketEffect.ShutdownConnection)
        .build()

    /** Сервер принял запрос и молчит — клиент упирается в таймаут. */
    fun stall() = MockResponse.Builder()
        .onResponseStart(SocketEffect.Stall)
        .build()
}

/** Задержки ответа, мс: имитация сети разной скорости. */
object Net {
    const val FAST = 2_000L
    const val MEDIUM = 4_000L
    const val SLOW = 12_000L
}

object Fixtures {
    private val categories = listOf("Phones", "Laptops", "Audio", "Cameras")

    fun item(id: Int, title: String = "Item $id", price: Double = id * 10.5) =
        Item(id = id, title = title, price = price, category = categories[id % categories.size])

    fun items(count: Int, from: Int = 1) = (from until from + count).map { item(it) }

    fun details(id: Int, inStock: Boolean = true) = item(id).let {
        ItemDetails(
            id = it.id,
            title = it.title,
            price = it.price,
            category = it.category,
            description = "Description of ${it.title}",
            inStock = inStock,
        )
    }
}
