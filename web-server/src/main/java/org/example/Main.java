package org.example;

import com.fastcgi.FCGIInterface;




import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

public class Main {
    private static final List<Result> results = Collections.synchronizedList(new ArrayList<>());

    // хранит поле одного результата (точки)
    static class Result {
        double x, y, r;
        boolean hit;
        String currentTime;
        double execTimeMs;

        public Result(double x, double y, double r, boolean hit, String currentTime, double execTimeMs) {
            this.x = x; this.y = y; this.r = r; this.hit = hit;
            this.currentTime = currentTime; this.execTimeMs = execTimeMs;
        }
    }

    public static void main(String[] args) {
        System.err.println(LocalDateTime.now() + " === FCGI Server Started ===");
        FCGIInterface fcgi = new FCGIInterface();
        int rc;
        try {
            rc = fcgi.FCGIaccept();
        } catch (Throwable t) {
            System.err.println(LocalDateTime.now() + " FATAL: Initial FCGIaccept() threw an exception.");
            t.printStackTrace(System.err);
            rc = -1;
        }

        
        while (rc >= 0) {
            try {
                processRequest();
            } catch (Throwable t) {
                System.err.println(LocalDateTime.now() + " ERROR: Unhandled exception in processRequest(): " + t);
                t.printStackTrace(System.err);
                try {
                    sendResponse(500, "Internal Server Error");
                } catch (Throwable ignored) { }
            }

            try {
                rc = fcgi.FCGIaccept();
            } catch (Throwable t) {
                System.err.println(LocalDateTime.now() + " ERROR: Subsequent FCGIaccept() threw an exception.");
                t.printStackTrace(System.err);
                rc = -1;
            }
        }
        System.err.println(LocalDateTime.now() + " === FCGI Server Shutting Down ===");
    }

    // обработать request
    private static void processRequest() {
        if (FCGIInterface.request == null) {
            return;
        }
        String method = FCGIInterface.request.params.getProperty("REQUEST_METHOD");

        if (method == null) {
            sendResponse(400, "Bad Request: No method specified.");
            return;
        }


        switch (method.toUpperCase(Locale.ROOT)) {
            case "POST":
                handlePostRequest();
                break;
            default:
                sendResponse(405, "Method Not Allowed");
                break;
        }
    }


    private static void handlePostRequest() {
        long start = System.nanoTime();
        String contentType = FCGIInterface.request.params.getProperty("CONTENT_TYPE");
        /*"application/x-www-form-urlencoded" - это стандартный MIME-тип,
        который используется для отправки данных HTML-форм при обычной (не-ajax-JSON) отправке
        или когда используется URLSearchParams/FormData в JS и отправляете как fetch с body
        в формате URLSearchParams.*/
        if (contentType == null || !contentType.startsWith("application/x-www-form-urlencoded")) {
            System.err.println(LocalDateTime.now() + " ERROR: Invalid Content-Type. Received: " + contentType);
            sendResponse(400, "Bad Content Type");
            return;
        }

        String clStr = FCGIInterface.request.params.getProperty("CONTENT_LENGTH");
        if (clStr == null) {
            System.err.println(LocalDateTime.now() + " ERROR: Content-Length is missing.");
            sendResponse(400, "Bad Request: Content-Length is missing.");
            return;
        }

        int cl;
        try {
            cl = Integer.parseInt(clStr.trim());
        } catch (NumberFormatException e) {
            System.err.println(LocalDateTime.now() + " ERROR: Content-Length parse error: " + clStr);
            sendResponse(400, "Bad Request: invalid Content-Length");
            return;
        }

        if (cl <= 0) {
            sendResponse(400, "Bad Request: Content-Length is <= zero.");
            return;
        }

        byte[] buffer = new byte[cl];
        try {
            InputStream in = System.in;
            int offset = 0;
            // эффективно читаем данные, используя буфер
            while (offset < cl) {
                int read = in.read(buffer, offset, cl - offset);
                if (read == -1) break;
                offset += read;
            }
            if (offset != cl) {
                System.err.println(LocalDateTime.now() + " ERROR: Incomplete read of request body. Expected " + cl + ", got " + offset);
                sendResponse(400, "Incomplete read");
                return;
            }
        } catch (IOException e) {
            System.err.println(LocalDateTime.now() + " ERROR: IOException while reading request body: " + e.getMessage());
            sendResponse(500, "Internal Server Error");
            return;
        }

        String body = new String(buffer, StandardCharsets.UTF_8);
        Map<String, String> params = parseBody(body);

        double x, y, r;
        // этот try catch не обязателен, тк мы ожидаем, что нам придут валидные данные от клиента
        // но, это было добавлено опять же, чтобы дебажить
        try {
            x = Double.parseDouble(params.getOrDefault("x", "NaN"));
            y = Double.parseDouble(params.getOrDefault("y", "NaN"));
            r = Double.parseDouble(params.getOrDefault("r", "NaN"));
            if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(r) ||
                    x < -5 || x > 3 || y < -3 || y > 5 || r < 1 || r > 5) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            System.err.println(LocalDateTime.now() + " ERROR: Invalid parameters received: " + body);
            sendResponse(400, "Invalid parameters");
            return;
        } catch (NullPointerException e) {
            System.err.println(LocalDateTime.now() + "incorrect request some field is null");
            sendResponse(400, "some parameters are empty");
            return;
        }

        boolean hit = isHit(x, y, r);
        double execTimeMs = (System.nanoTime() - start) / 1_000_000.0;
        String currentTime = LocalDateTime.now().toString();

        Result result = new Result(x, y, r, hit, currentTime, execTimeMs);
        results.add(result);

        String json = toJson(results);
        System.out.print("Status: 200 OK\r\n");
        System.out.print("Content-Type: application/json; charset=utf-8\r\n");
        System.out.print("\r\n");
        System.out.print(json);
        System.out.flush();
    }

    // определяем попал/нет
    private static boolean isHit(double x, double y, double r) {
        // Первая четверть (прямоугольник)
        if (x >= 0 && y >= 0) {
            return (x <= r && y <= (r/2));
        }
        // Вторая четверть

        // Третья четверть (четверть круга)
        if (x <= 0 && y <= 0) {
            return ((x * x + y * y) <= (r/2));
        }
        // Четвертая четверть (треугольник)
        if (x >= 0 && y <= 0) {
            return (x - 2 <=  y);
        }
        // Вторая четверть
        return false;
    }

    // преобразуем строку в Map
    private static Map<String, String> parseBody(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isEmpty()) return map;
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) map.put(kv[0], java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return map;
    }

    // парсим объект в JSON
    private static String toJson(List<Result> results) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < results.size(); i++) {
            Result res = results.get(i);
            sb.append("{")
                    .append("\"x\":").append(res.x).append(",")
                    .append("\"y\":").append(res.y).append(",")
                    .append("\"r\":").append(res.r).append(",")
                    .append("\"hit\":").append(res.hit).append(",")
                    .append("\"currentTime\":\"").append(res.currentTime).append("\",")
                    .append("\"execTime\":").append(String.format(Locale.US, "%.4f", res.execTimeMs))
                    .append("}");
            if (i < results.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }


    private static void sendResponse(int status, String message) {
        System.out.print("Status: " + status + "\r\n");
        System.out.print("Content-Type: application/json; charset=utf-8\r\n");
        System.out.print("\r\n");
        System.out.print("{\"error\": \"" + message + "\"}");
        System.out.flush();
    }

}
