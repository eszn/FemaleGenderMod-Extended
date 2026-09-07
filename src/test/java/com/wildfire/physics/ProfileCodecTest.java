package com.wildfire.physics;

import com.wildfire.main.entitydata.BodySettings;
import com.wildfire.main.networking.BodySync;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProfileCodecTest {
    @Test void allShapesAndMaximumSizeRoundTripIn42Bytes() {
        for(var shape:BodySettings.BreastShape.values()) {
            var p=new BodySync.Profile(UUID.randomUUID(),1.2f,new BodySettings(1,1,1,1,shape,true,1));
            var buffer=Unpooled.buffer();
            try { BodySync.CODEC.encode(buffer,p); assertEquals(42,buffer.readableBytes()); assertEquals(p,BodySync.CODEC.decode(buffer)); assertFalse(buffer.isReadable()); }
            finally { buffer.release(); }
        }
    }
    @Test void nonFiniteAndOutOfRangeValuesAreRejectedAtEveryBoundary() {
        for(float invalid:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-.01f,1.21f}) {
            assertThrows(IllegalArgumentException.class,()->new BodySync.Profile(UUID.randomUUID(),invalid,BodySettings.DEFAULT));
            for(int field=0;field<5;field++) {
                float[] v={0,0,0,0,0}; v[field]=invalid;
                assertThrows(IllegalArgumentException.class,()->new BodySettings(v[0],v[1],v[2],v[3],BodySettings.BreastShape.ROUNDED,true,v[4]));
            }
        }
    }
    @Test void maliciousWireFloatsAndEnumAreRejected() {
        var p=new BodySync.Profile(UUID.randomUUID(),1,BodySettings.DEFAULT);
        for(int offset:new int[]{16,20,24,28,32,38}) {
            var buffer=Unpooled.buffer();
            try { BodySync.CODEC.encode(buffer,p); buffer.setFloat(offset,Float.NaN); assertThrows(DecoderException.class,()->BodySync.CODEC.decode(buffer)); }
            finally { buffer.release(); }
        }
        var buffer=Unpooled.buffer();
        try { BodySync.CODEC.encode(buffer,p); buffer.setByte(36,255); assertThrows(DecoderException.class,()->BodySync.CODEC.decode(buffer)); }
        finally { buffer.release(); }
    }
    @Test void everyTruncationFailsBeforeAProfileCanBeApplied() {
        var buffer=Unpooled.buffer();
        try {
            BodySync.CODEC.encode(buffer,new BodySync.Profile(UUID.randomUUID(),1,BodySettings.DEFAULT));
            for(int length=0;length<42;length++) {
                var truncated=buffer.slice(0,length);
                assertThrows(DecoderException.class,()->BodySync.CODEC.decode(truncated));
            }
        } finally { buffer.release(); }
    }
}
