package com.wildfire.client.render;

import com.wildfire.physics.AuthoredBodyMesh;
import com.wildfire.main.WildfireGender;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Optional locally imported geometry. Public builds continue to work without the supplied asset. */
public final class AuthoredModel {
    private AuthoredModel() {}
    private static final class Holder {
        static final AuthoredBodyMesh MODEL=load();
        private static AuthoredBodyMesh load() {
            try(var stream=AuthoredModel.class.getResourceAsStream("/assets/wildfire_gender/body/jenny-mesh.json")) {
                if(stream==null) return null;
                try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) { return AuthoredBodyMesh.read(reader); }
            } catch(Exception e) { WildfireGender.LOGGER.error("Unable to read imported body model",e); return null; }
        }
    }
    public static AuthoredBodyMesh get() { return Holder.MODEL; }
}
