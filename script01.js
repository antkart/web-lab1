// === Отрисовка области ===
function drawArea(R) {
    const canvas = document.getElementById("myCanvas");
    const ctx = canvas.getContext("2d");

    const width = canvas.width;
    const height = canvas.height;
    ctx.clearRect(0, 0, width, height);

    const r = 15 * parseFloat(R) / 2;

    // прямоугольник
    ctx.beginPath();
    ctx.moveTo(width/2, height/2);
    ctx.lineTo(width/2, height/2 - 2 * r);
    ctx.lineTo(width/2 + 4 * r, height/2 - 2 * r);
    ctx.lineTo(width/2 + 4 * r, height/2);
    ctx.closePath();
    ctx.fillStyle = "blue";
    ctx.fill();

    // треугольник
    ctx.beginPath();
    ctx.moveTo(width/2, height/2);
    ctx.lineTo(width/2 + 2*r, height/2);
    ctx.lineTo(width/2, height/2 + 2 * r);
    ctx.closePath();
    ctx.fillStyle = "blue";
    ctx.fill();

    // сектор
    const x = width/2;
    const y = height/2;
    ctx.beginPath();
    ctx.moveTo(x, y);
    ctx.arc(x, y, 2 * r, Math.PI/2, Math.PI, false);
    ctx.closePath();
    ctx.fillStyle = "blue";
    ctx.fill();

    // оси координат
    const step = 30;
    ctx.strokeStyle = "#000";
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(0, height / 2);
    ctx.lineTo(width, height / 2);
    ctx.moveTo(width / 2, 0);
    ctx.lineTo(width / 2, height);
    ctx.stroke();

    ctx.fillStyle = "#000";
    ctx.font = "12px Arial";

    for (let i = -5; i <= 5; i++) {
        const xPos = width / 2 + i * step;
        const yPos = height / 2 + i * step;
        if (i !== 0) {
            ctx.beginPath();
            ctx.moveTo(xPos, height / 2 - 5);
            ctx.lineTo(xPos, height / 2 + 5);
            ctx.stroke();
            ctx.fillText(i, xPos - 3, height / 2 + 15);

            ctx.beginPath();
            ctx.moveTo(width / 2 - 5, yPos);
            ctx.lineTo(width / 2 + 5, yPos);
            ctx.stroke();
            ctx.fillText(-i, width / 2 + 10, yPos + 5);
        }
    }
}

document.addEventListener("DOMContentLoaded", () => {
    // === Разрешаем выбирать только один R ===
    const rCheckboxes = document.querySelectorAll('input[name="param-R"]');
    rCheckboxes.forEach(cb => {
        cb.addEventListener("change", () => {
            if (cb.checked) {
                rCheckboxes.forEach(other => {
                    if (other !== cb) other.checked = false;
                });
            }
        });
    });

    // === Разрешаем выбирать только один Y ===
    const yCheckboxes = document.querySelectorAll('input[name="param-Y"]');
    yCheckboxes.forEach(cb => {
        cb.addEventListener("change", () => {
            if (cb.checked) {
                yCheckboxes.forEach(other => {
                    if (other !== cb) other.checked = false;
                });
            }
        });
    });
});

// === Отправка данных на сервер ===
async function sendRequest(x, y, r) {
    const params = new URLSearchParams({ x, y, r });

    try {
        const response = await fetch("/fcgi-bin/web-server-1.0-SNAPSHOT-shaded.jar", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: params.toString()
        });

        if (!response.ok) throw new Error(`Ошибка HTTP ${response.status}`);

        const data = await response.json();

        console.log("Ответ сервера:", data);

        // Обновляем таблицу
        const table = document.getElementById("result-table");
        const last = Array.isArray(data) ? data[data.length - 1] : data;

        const row = table.insertRow();
        row.insertCell(0).innerText = last.x; // строки, без округления
        row.insertCell(1).innerText = last.y;
        row.insertCell(2).innerText = last.r;
        row.insertCell(3).innerText = last.hit ? "Попадает" : "Не попадает";
        row.insertCell(4).innerText = last.currentTime || new Date().toLocaleTimeString();
        row.insertCell(5).innerText = last.execTime || "-";

    } catch (err) {
        console.error("Ошибка при отправке:", err);
        alert("Не удалось связаться с сервером.");
    }
}

// === Обработка формы ===
const form = document.getElementById("main-form");

form.addEventListener("submit", (e) => {
    e.preventDefault();

    const xValue = document.querySelector('input[id="x"]').value.trim();
    const yValue = Array.from(document.querySelectorAll('input[name="param-Y"]'))
        .find(cb => cb.checked)?.value;
    const rValue = Array.from(document.querySelectorAll('input[name="param-R"]'))
        .find(cb => cb.checked)?.value;

    // простая валидация через строку
    if (xValue === "" || isNaN(Number(xValue))) {
        alert("Введите число X от -5 до 3");
        return;
    }
    const xNum = Number(xValue);
    if (xNum < -5 || xNum > 3) {
        alert("Введите число X от -5 до 3");
        return;
    }

    if (!yValue) {
        alert("Выберите значение Y");
        return;
    }
    if (!rValue) {
        alert("Выберите значение R");
        return;
    }

    sendRequest(xValue, yValue, rValue);
});

// === При изменении R перерисовываем область ===
document.querySelectorAll('input[name="param-R"]').forEach(cb => {
    cb.addEventListener("change", () => {
        if (cb.checked) drawArea(cb.value);
    });
});

drawArea(1);
