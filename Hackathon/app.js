let isLogin = true;

function toggleForm() {
    isLogin = !isLogin;
    document.getElementById("form-title").innerText = isLogin ? "Login" : "Register";
    document.getElementById("submit-btn").innerText = isLogin ? "Login" : "Register";
    
    document.getElementById("toggle-text").innerHTML = isLogin ? 
        `Don't have an account? <span id="toggle-link" onclick="toggleForm()">Sign Up</span>` : 
        `Already have an account? <span id="toggle-link" onclick="toggleForm()">Login</span>`;
    
    if (isLogin) {
        document.getElementById("register-fields").classList.add("hidden");
        document.getElementById("submit-btn").setAttribute("onclick", "login()");
    } else {
        document.getElementById("register-fields").classList.remove("hidden");
        document.getElementById("submit-btn").setAttribute("onclick", "register()");
    }
    document.getElementById("message").innerText = "";
}

async function login() {
    const email = document.getElementById("email").value;
    const password = document.getElementById("password").value;
    const msgElement = document.getElementById("message");

    if (!email || !password) {
        msgElement.style.color = "#b01a2e";
        msgElement.innerText = "Please enter email and password.";
        return;
    }

    msgElement.style.color = "black";
    msgElement.innerText = "Connecting...";

    const formData = new URLSearchParams();
    formData.append("email", email);
    formData.append("password", password);

    try {
        const response = await fetch("http://localhost:8080/api/login", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formData.toString()
        });

        const result = await response.text();

        if (response.ok) {
            msgElement.style.color = "#28a745";
            msgElement.innerText = "Login successful! Redirecting...";
            
            const resultParts = result.split("|"); 
            localStorage.setItem("userRole", resultParts[1]); 
            localStorage.setItem("userEmail", email); 
            
            // Redirects to dashboard instead of flipping the form
            setTimeout(() => {
                window.location.href = "dashboard.html";
            }, 1000);
        } else {
            msgElement.style.color = "#b01a2e";
            msgElement.innerText = result; 
        }
    } catch (e) {
        msgElement.style.color = "#b01a2e";
        msgElement.innerText = "Database connection failed. Is the server running?";
    }
}

async function register() {
    const name = document.getElementById("name").value;
    const orgName = document.getElementById("org-name").value; // NEW
    const role = document.getElementById("reg-role").value;
    const roll = document.getElementById("reg-roll").value;
    const dept = document.getElementById("department").value;
    const email = document.getElementById("email").value;
    const password = document.getElementById("password").value;
    const msgElement = document.getElementById("message");

    if (!name || !email || !password || !roll) {
        msgElement.style.color = "#b01a2e";
        msgElement.innerText = "Please fill out all required fields.";
        return;
    }

    msgElement.style.color = "black";
    msgElement.innerText = "Connecting...";

    const formData = new URLSearchParams();
    formData.append("name", name);
    formData.append("organization", orgName); // NEW
    formData.append("role", role);
    formData.append("roll_number", roll);
    formData.append("department", dept);
    formData.append("email", email);
    formData.append("password", password);

    try {
        const response = await fetch("http://localhost:8080/api/register", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: formData.toString()
        });

        if (response.ok) {
            msgElement.style.color = "#28a745";
            msgElement.innerText = "Registration successful! Please login.";
            
            setTimeout(() => {
                toggleForm();
                // Wipes the text boxes clean after registration
                document.getElementById("name").value = "";
                document.getElementById("reg-roll").value = "";
                document.getElementById("department").value = "";
                document.getElementById("email").value = "";
                document.getElementById("password").value = ""; 
            }, 1500);
        } else {
            msgElement.style.color = "#b01a2e";
            msgElement.innerText = "Registration failed. Email might already exist.";
        }
    } catch (e) {
        msgElement.style.color = "#b01a2e";
        msgElement.innerText = "Database connection failed. Is the server running?";
    }
}

// Allows you to press 'Enter' to submit the form without clicking the button
document.addEventListener('keypress', function (e) {
    if (e.key === 'Enter') {
        if (isLogin) {
            login();
        } else {
            register();
        }
    }
});