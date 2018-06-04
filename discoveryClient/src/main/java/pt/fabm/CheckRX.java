package pt.fabm;

import io.reactivex.Single;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.HttpResponse;

public class CheckRX {

    public static Single<HttpResponse<Buffer>> checkResponseOK(HttpResponse<Buffer> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return Single.error(new IllegalStateException(response.statusMessage()));
        }
        return Single.just(response);
    }

    private CheckRX(){
    }
}
