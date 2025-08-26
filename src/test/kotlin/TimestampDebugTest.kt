package soia.internal.soia

import org.junit.jupiter.api.Test
import soia.JsonFlavor
import java.time.Instant

class TimestampDebugTest {

    @Test
    fun `debug timestamp serialization`() {
        val epoch = Instant.EPOCH
        println("Original: $epoch")
        println("Epoch millis: ${epoch.toEpochMilli()}")
        
        val denseJson = Serializers.timestamp.toJsonCode(epoch, JsonFlavor.DENSE)
        println("Dense JSON: $denseJson")
        
        val restored = Serializers.timestamp.fromJsonCode(denseJson)
        println("Restored: $restored")
        println("Restored millis: ${restored.toEpochMilli()}")
        
        println("Equal? ${epoch == restored}")
    }
    
    @Test
    fun `debug timestamp serialization with real timestamp`() {
        val timestamp = Instant.parse("2025-08-25T10:30:45Z")
        println("Original: $timestamp")
        println("Original millis: ${timestamp.toEpochMilli()}")
        
        val denseJson = Serializers.timestamp.toJsonCode(timestamp, JsonFlavor.DENSE)
        println("Dense JSON: $denseJson")
        
        val restored = Serializers.timestamp.fromJsonCode(denseJson)
        println("Restored: $restored")
        println("Restored millis: ${restored.toEpochMilli()}")
        
        println("Equal? ${timestamp == restored}")
    }
}
