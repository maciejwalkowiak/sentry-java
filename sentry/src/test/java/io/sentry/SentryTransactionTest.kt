package io.sentry

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.function.Supplier
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull


class SentryTransactionTest {

    @Test
    fun `when transaction is created, startTimestamp is set`() {
        val transaction = SentryTransaction("name")
        assertNotNull(transaction.startTimestamp)
    }

    @Test
    fun `when transaction is created, timestamp is not set`() {
        val transaction = SentryTransaction("name")
        assertNull(transaction.timestamp)
    }

    @Test
    fun `when transaction is created, context is set`() {
        val transaction = SentryTransaction("name")
        assertNotNull(transaction.contexts)
    }

    @Test
    fun `when transaction is created, by default is not sampled`() {
        val transaction = SentryTransaction("name")
        assertNull(transaction.isSampled)
    }

    @Test
    fun `when transaction is finished, timestamp is set`() {
        val transaction = SentryTransaction("name")
        transaction.finish()
        assertNotNull(transaction.timestamp)
    }

    @Test
    fun `when transaction is finished with status, timestamp and status are set`() {
        val transaction = SentryTransaction("name")
        transaction.finish(SpanStatus.ABORTED)
        assertNotNull(transaction.timestamp)
        assertEquals(SpanStatus.ABORTED, transaction.status)
    }

    @Test
    fun `when transaction is finished, transaction is captured`() {
        val hub = mock<IHub>()
        val transaction = SentryTransaction("name", SpanContext(), hub)
        transaction.finish()
        verify(hub).captureTransaction(transaction, null)
    }

    @Test
    fun `when transaction with throwable set is finished, span context is associated with throwable`() {
        val hub = mock<IHub>()
        val transaction = SentryTransaction("name", SpanContext(), hub)
        val ex = RuntimeException()
        transaction.throwable = ex
        transaction.finish()
        verify(hub).setSpanContext(ex, transaction)
    }

    @Test
    fun `returns sentry-trace header`() {
        val transaction = SentryTransaction("name")

        assertNotNull(transaction.toSentryTrace())
    }

    @Test
    fun `starting child creates a new span`() {
        val transaction = SentryTransaction("name")
        val span = transaction.startChild("op") as Span
        assertNotNull(span)
        assertNotNull(span.spanId)
        assertNotNull(span.startTimestamp)
    }

    @Test
    fun `starting child adds a span to transaction`() {
        val transaction = SentryTransaction("name")
        val span = transaction.startChild("op")
        assertEquals(1, transaction.spans.size)
        assertEquals(span, transaction.spans.first())
    }

    @Test
    fun `span created with startChild has parent span id the same as transaction span id`() {
        val transaction = SentryTransaction("name")
        val span = transaction.startChild("op") as Span
        assertEquals(transaction.spanId, span.parentSpanId)
    }

    @Test
    fun `span created with startChild has the same trace id as transaction`() {
        val transaction = SentryTransaction("name")
        val span = transaction.startChild("op") as Span
        assertEquals(transaction.traceId, span.traceId)
    }

    @Test
    fun `starting child with operation and description creates a new span`() {
        val transaction = SentryTransaction("name")
        val span = transaction.startChild("op", "description") as Span
        assertNotNull(span)
        assertNotNull(span.spanId)
        assertNotNull(span.startTimestamp)
        assertEquals("op", span.operation)
        assertEquals("description", span.description)
    }

    @Test
    fun `starting child with operation and description adds a span to transaction`() {
        val transaction = SentryTransaction("name")
        val span = transaction.startChild("op", "description")
        assertEquals(1, transaction.spans.size)
        assertEquals(span, transaction.spans.first())
    }

    @Test
    fun `span created with startChild with operation and description has parent span id the same as transaction span id`() {
        val transaction = SentryTransaction("name")
        val span = transaction.startChild("op", "description") as Span
        assertEquals(transaction.spanId, span.parentSpanId)
    }

    @Test
    fun `span created with startChild with operation and description has the same trace id as transaction`() {
        val transaction = SentryTransaction("name")
        val span = transaction.startChild("op", "description") as Span
        assertEquals(transaction.traceId, span.traceId)
    }

    @Test
    fun `setting op sets op on TraceContext`() {
        val transaction = SentryTransaction("name")
        transaction.operation = "op"
        transaction.finish()
        assertEquals("op", transaction.contexts.trace!!.operation)
    }

    @Test
    fun `setting description sets description on TraceContext`() {
        val transaction = SentryTransaction("name")
        transaction.description = "desc"
        transaction.finish()
        assertEquals("desc", transaction.contexts.trace!!.description)
    }

    @Test
    fun `setting status sets status on TraceContext`() {
        val transaction = SentryTransaction("name")
        transaction.status = SpanStatus.ALREADY_EXISTS
        transaction.finish()
        assertEquals(SpanStatus.ALREADY_EXISTS, transaction.contexts.trace!!.status)
    }

    @Test
    fun `setName overwrites the transaction name`() {
        val transaction = SentryTransaction("initial name")
        transaction.name = "new name"
        assertEquals("new name", transaction.transaction)
    }

    val pool = Executors.newFixedThreadPool(200)

    @Test
    fun `fuzzy`() {
        Sentry.init {
            it.dsn = "https://6ae91e8b6e1d41ffb25e891faf2a63a5@o420886.ingest.sentry.io/5593200"
            it.setDebug(true)
            it.tracesSampleRate = 1.0
            it.setTransportFactory { _, _ -> mock() }
        }


        val transaction = Sentry.startTransaction("foo")
        val futures = mutableListOf<CompletableFuture<out Any>>()

        for (i in 1..10000) {
            Thread.sleep(Random.nextLong(0, 20))
            futures += CompletableFuture.supplyAsync(Supplier {
                if (Random.nextInt() % 2 == 0) {
                    val inner = Sentry.startTransaction("transaction-$i")
                    addSpans(inner, i)
                    inner.finish()
                } else {
                    addSpans(transaction, i)
                }
            }, pool)
        }
        futures.forEach { it.join() }
        transaction.finish()
    }

    private fun addSpans(transaction: ITransaction, i: Int) {
        Thread.sleep(Random.nextLong(0, 20))
        val span = transaction.startChild("child-${i}")
        span.description = "description-${i}"
        transaction.setTag("child", "$i")
        transaction.contexts["child-$i"] = i
        val spanFutures = mutableListOf<CompletableFuture<out Any>>()
        for (j in 1..100) {
            if (Random.nextInt() % 2 == 0) {
                spanFutures += CompletableFuture.supplyAsync(Supplier {
                    val child = span.startChild("child-child-$i-$j")
                    child.setTag("child-child", "$i-$j")
                    child.finish()
                }, pool)
            }
        }
        span.spanContext.setTag("foo-$i", "bar-$i")
        Thread.sleep(Random.nextLong(0, 20))
        spanFutures.forEach { it.join() }
        span.finish()
    }
}
