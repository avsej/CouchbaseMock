/**
 * Copyright 2012 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.couchbase.mock.http;

import org.couchbase.mock.CouchbaseMock;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.couchbase.mock.Bucket;

/**
 *
 * @author Sergey Avseyev
 */
public class ViewsHandler implements HttpHandler {

    public class ViewRequest {

        private final HttpExchange exchange;
        private final CouchbaseMock mock;
        private JSONObject payload;

        private ViewRequest(CouchbaseMock mock, HttpExchange exchange) throws HTTPException {
            this.mock = mock;
            this.exchange = exchange;
            String path = exchange.getRequestURI().getPath();
            /*
             * Supported endpoints:
             *
             *  GET /:bucketName
             *  GET /:bucketName/_all_docs
             *  GET /:bucketName/_design/:designName
             *  GET /:bucketName/_design/:designName/_view/:viewName
             *  PUT /:bucketName/_design/:designName
             *  DELETE /:bucketName/_design/:designName
             */
            StringTokenizer tokenizer = new StringTokenizer(path, "/");
            if (tokenizer.countTokens() < 0) {
                throw new HTTPException(HttpURLConnection.HTTP_NOT_FOUND, "no_couchbase_bucket_exists");
            }
            String bucketName = tokenizer.nextToken();
            Bucket bucket = mock.getBuckets().get(bucketName);
            if (bucket == null) {
                throw new HTTPException(HttpURLConnection.HTTP_NOT_FOUND, "no_couchbase_bucket_exists");
            }
            if (exchange.getRequestMethod().equals("GET")) {
            } else if (exchange.getRequestMethod().equals("PUT")) {
                if (tokenizer.countTokens() != 2) {
                    throw new HTTPException(HttpURLConnection.HTTP_BAD_REQUEST, "Only reserved document ids may start with underscore");
                }
                String designDocId = tokenizer.nextToken() + tokenizer.nextToken();
            } else if (exchange.getRequestMethod().equals("DELETE")) {
            } else {
                throw new HTTPException(HttpURLConnection.HTTP_BAD_METHOD, "Only GET,PUT,DELETE allowed");
            }
        }

        public byte[] execute() throws HTTPException {
            return new byte[]{};
        }
    }
    private final CouchbaseMock mock;

    private class HTTPException extends Throwable {

        private final String reason;
        private final int code;
        private String error;

        public HTTPException(int code, String reason) {
            this.code = code;
            this.reason = reason;
            switch (code) {
                case 404:
                    this.error = "not_found";
                    break;
                case 405:
                    this.error = "method_not_allowed";
                    break;
            }
        }

        public String toJSON() {
            JSONObject json = new JSONObject();
            json.put("error", error);
            json.put("error", reason);
            return json.toString();
        }
    }

    public ViewsHandler(CouchbaseMock mock) {
        this.mock = mock;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        OutputStream body = exchange.getResponseBody();
        try {
            ViewRequest request = new ViewRequest(mock, exchange);
            byte[] payload;

            payload = request.execute();
            if (payload != null) {
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, payload.length);
                body.write(payload);
            } else {
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_INTERNAL_ERROR, -1);
            }
        } catch (HTTPException ex) {
            body.write(ex.toJSON().getBytes());
            exchange.sendResponseHeaders(ex.code, -1);
        } catch (Throwable ex) {
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_INTERNAL_ERROR, -1);
        } finally {
            body.close();
        }
    }
}
