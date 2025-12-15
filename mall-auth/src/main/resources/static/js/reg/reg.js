function sendCode() {
    var phone = document.getElementById("phone").value;
    if (!phone) {
        alert("请输入手机号");
        return;
    }
    if (!/^1[3-9]\d{9}$/.test(phone)) {
        alert("手机号格式不正确");
        return;
    }
    fetch('/sms/sendcode?phone=' + phone)
        .then(response => response.json())
        .then(data => {
            if (data.code === 0) {
                alert("Mock验证码: " + data.smsCode);
            } else {
                alert("发送失败");
            }
        });
}

// Check on blur
document.addEventListener("DOMContentLoaded", function () {
    var inputs = document.getElementsByTagName("input");
    inputs["userName"].onblur = checkUsername;
    inputs["password"].onblur = checkPassword;
    inputs["phone"].onblur = checkPhone;
});

function checkUsername() {
    var userName = document.getElementsByTagName("input")["userName"].value;
    // Simple logic: just alert or show error msg if invalid.
    // User asked "remind him format is wrong after blur".
    // I should find the error-msg div relative to input or just use alerts (user mentioned "remind him").
    // To be nice, let's look for sibling error-msg.
    var errorDiv = document.getElementsByTagName("input")["userName"].nextElementSibling;
    if (userName.length < 6 || userName.length > 18) {
        errorDiv.innerText = "用户名必须是6-18位字符";
        errorDiv.style.color = "red";
        return false;
    } else {
        errorDiv.innerText = "";
        return true;
    }
}

function checkPassword() {
    var password = document.getElementsByTagName("input")["password"].value;
    var errorDiv = document.getElementsByTagName("input")["password"].nextElementSibling;
    if (password.length < 6 || password.length > 18) {
        errorDiv.innerText = "密码必须是6-18位字符";
        errorDiv.style.color = "red";
        return false;
    } else {
        errorDiv.innerText = "";
        return true;
    }
}

function checkPhone() {
    var phone = document.getElementsByTagName("input")["phone"].value;
    var errorDiv = document.getElementsByTagName("input")["phone"].nextElementSibling;
    if (!/^1[3-9]\d{9}$/.test(phone)) {
        errorDiv.innerText = "手机号格式不正确";
        errorDiv.style.color = "red";
        return false;
    } else {
        errorDiv.innerText = "";
        return true;
    }
}

function submitRegister() {
    var u = checkUsername();
    var p = checkPassword();
    var ph = checkPhone();
    var code = document.getElementsByTagName("input")["code"].value;

    if (!u || !p || !ph) {
        return false;
    }

    if (code === "") {
        alert("验证码必须填写");
        return false;
    }
    return true;
}
