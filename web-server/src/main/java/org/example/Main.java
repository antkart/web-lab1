package org.example;

import com.fastcgi.FCGIInterface;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public class Main {
    private static final List<Result> results = Collections.synchronizedList(new ArrayList<>());

    // предопределённые BigDecimal константы
    private static final BigDecimal BD_NEG_5 = new BigDecimal("-5");
    private static final BigDecimal BD_3 = new BigDecimal("3");
    private static final BigDecimal BD_NEG_3 = new BigDecimal("-3");
    private static final BigDecimal BD_5 = new BigDecimal("5");
    private static final BigDecimal BD_1 = new BigDecimal("1");
    private static final BigDecimal BD_2 = new BigDecimal("2");
    private static final BigDecimal BD_4 = new BigDecimal("4");
    private static final BigDecimal BD_0 = BigDecimal.ZERO;

    static class Result {
        BigDecimal x;
        BigDecimal y;
        BigDecimal r;
        boolean hit;
        String currentTime;
        double execTimeMs;

        public Result(BigDecimal x, BigDecimal y, BigDecimal r, boolean hit, String currentTime, double execTimeMs) {
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

        String body = new String(buffer, java.nio.charset.StandardCharsets.UTF_8);
        Map<String, String> params = parseBody(body);

        BigDecimal x, y, r;
        try {
            String sx = params.get("x");
            String sy = params.get("y");
            String sr = params.get("r");
            if (sx == null || sy == null || sr == null) {
                System.err.println(LocalDateTime.now() + " ERROR: Missing parameters in body: " + body);
                sendResponse(400, "some parameters are empty");
                return;
            }

            // Парсим строками
            x = new BigDecimal(sx);
            y = new BigDecimal(sy);
            r = new BigDecimal(sr);

            // Валидация диапазонов x
            if (x.compareTo(BD_NEG_5) < 0 || x.compareTo(BD_3) > 0 ||
                    y.compareTo(BD_NEG_3) < 0 || y.compareTo(BD_5) > 0 ||
                    r.compareTo(BD_1) < 0 || r.compareTo(BD_5) > 0) {
                throw new NumberFormatException("Out of allowed ranges");
            }
        } catch (NumberFormatException e) {
            System.err.println(LocalDateTime.now() + " ERROR: Invalid parameters received or out of range. Body: " + body + " Error: " + e.getMessage());
            sendResponse(400, "Invalid parameters");
            return;
        } catch (NullPointerException e) {
            System.err.println(LocalDateTime.now() + " ERROR: Null parameter encountered. Body: " + body);
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

    private static boolean isHit(BigDecimal x, BigDecimal y, BigDecimal r) {
        // Первая четверть (прямоугольник)
        if (x.compareTo(BD_0) >= 0 && y.compareTo(BD_0) >= 0) {
            // проверяем x <= r и (y * 2) <= r
            if (x.compareTo(r) <= 0) {
                BigDecimal yTimes2 = y.multiply(BD_2);
                return yTimes2.compareTo(r) <= 0;
            }
            return false;
        }

        // Третья четверть (четверть круга) x <= 0 && y <= 0:
        if (x.compareTo(BD_0) <= 0 && y.compareTo(BD_0) <= 0) {
            // вычисляем x^2 + y^2 (точно), затем умножаем обе части на 4:
            BigDecimal x2 = x.multiply(x);
            BigDecimal y2 = y.multiply(y);
            BigDecimal left = x2.add(y2);
            BigDecimal leftTimes4 = left.multiply(BD_4);

            BigDecimal r2 = r.multiply(r);
            // сравниваем: 4*(x^2 + y^2) <= r^2
            return leftTimes4.compareTo(r2) <= 0;
        }

        // Четвёртая четверть (треугольник) x >= 0 && y <= 0: условие x - 2 <= y
        if (x.compareTo(BD_0) >= 0 && y.compareTo(BD_0) <= 0) {
            BigDecimal left = x.subtract(BD_2);
            return left.compareTo(y) <= 0;
        }

        // Вторая четверть
        return false;
    }

    private static Map<String, String> parseBody(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isEmpty()) return map;
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                String key = java.net.URLDecoder.decode(kv[0], java.nio.charset.StandardCharsets.UTF_8);
                String value = java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
                map.put(key, value);
            }
        }
        return map;
    }

    private static String toJson(List<Result> results) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < results.size(); i++) {
            Result res = results.get(i);
            sb.append("{")
                    .append("\"x\":\"").append(res.x.toPlainString()).append("\",")
                    .append("\"y\":\"").append(res.y.toPlainString()).append("\",")
                    .append("\"r\":\"").append(res.r.toPlainString()).append("\",")
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
