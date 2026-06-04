// API Base URL (Relative paths since frontend is hosted on same server)
const API_BASE = "";

// Global Application State
let state = {
    token: localStorage.getItem("jwt_token") || null,
    user: JSON.parse(localStorage.getItem("user_info")) || null,
    activeView: null,
    teacher: {
        subjects: [],
        activeSubjectId: null,
        studentsStats: []
    },
    admin: {
        users: [],
        subjects: [],
        teachers: [],
        students: []
    },
    stompClient: null
};

// On App Load
document.addEventListener("DOMContentLoaded", () => {
    // Set default date for attendance marker to today
    const dateInput = document.getElementById("attendance-date");
    if (dateInput) {
        dateInput.value = new Date().toISOString().substring(0, 10);
    }
    
    if (state.token && state.user) {
        showDashboard();
    } else {
        showAuth();
    }
});

// ================= AUTHENTICATION & VIEWS =================

function showAuth() {
    state.token = null;
    state.user = null;
    localStorage.removeItem("jwt_token");
    localStorage.removeItem("user_info");
    
    if (state.stompClient && state.stompClient.connected) {
        state.stompClient.disconnect();
    }

    document.getElementById("dashboard-section").classList.remove("active");
    document.getElementById("auth-section").classList.add("active");
    switchAuthTab("login");
}

function showDashboard() {
    document.getElementById("auth-section").classList.remove("active");
    document.getElementById("dashboard-section").classList.add("active");
    
    // Set user profile display name
    document.getElementById("user-display").innerHTML = `<i class="fa-solid fa-circle-user"></i> ${state.user.username} (${getRoleLabel(state.user.role)})`;
    
    renderSidebar();
    initializeWebSocket();
    
    // Direct to correct view based on role
    if (state.user.role === "ROLE_ADMIN") {
        switchView("admin");
    } else if (state.user.role === "ROLE_TEACHER") {
        switchView("teacher");
    } else if (state.user.role === "ROLE_STUDENT") {
        switchView("student");
    }
}

function getRoleLabel(role) {
    if (role === "ROLE_ADMIN") return "Admin";
    if (role === "ROLE_TEACHER") return "Teacher";
    if (role === "ROLE_STUDENT") return "Student";
    return role;
}

function switchAuthTab(tab) {
    const loginTabBtn = document.getElementById("login-tab-btn");
    const regTabBtn = document.getElementById("register-tab-btn");
    const loginForm = document.getElementById("login-form");
    const regForm = document.getElementById("register-form");

    if (tab === "login") {
        loginTabBtn.classList.add("active");
        regTabBtn.classList.remove("active");
        loginForm.classList.add("active");
        regForm.classList.remove("active");
    } else {
        loginTabBtn.classList.remove("active");
        regTabBtn.classList.add("active");
        loginForm.classList.remove("active");
        regForm.classList.add("active");
    }
}

function switchView(viewName) {
    state.activeView = viewName;
    
    // Toggle active view panel
    document.querySelectorAll(".view-panel").forEach(panel => {
        panel.classList.remove("active");
    });
    document.getElementById(`${viewName}-view`).classList.add("active");
    
    // Toggle active sidebar nav link
    document.querySelectorAll(".nav-link").forEach(link => {
        link.classList.remove("active");
    });
    const activeLink = document.getElementById(`nav-${viewName}`);
    if (activeLink) activeLink.classList.add("active");
    
    // Load data for view
    if (viewName === "admin") {
        loadAdminData();
    } else if (viewName === "teacher") {
        loadTeacherSubjects();
    } else if (viewName === "student") {
        loadStudentAttendance();
    } else if (viewName === "mentor") {
        loadMentorData();
    }
}

function renderSidebar() {
    const navContainer = document.getElementById("sidebar-nav");
    navContainer.innerHTML = "";
    
    if (state.user.role === "ROLE_ADMIN") {
        navContainer.innerHTML = `
            <a id="nav-admin" class="nav-link" onclick="switchView('admin')">
                <i class="fa-solid fa-user-shield"></i> Admin Control Panel
            </a>
        `;
    } else if (state.user.role === "ROLE_TEACHER") {
        navContainer.innerHTML = `
            <a id="nav-teacher" class="nav-link" onclick="switchView('teacher')">
                <i class="fa-solid fa-chalkboard-user"></i> Subject Dashboard
            </a>
            <a id="nav-mentor" class="nav-link" onclick="switchView('mentor')">
                <i class="fa-solid fa-graduation-cap"></i> Class Mentor Overview
            </a>
        `;
    } else if (state.user.role === "ROLE_STUDENT") {
        navContainer.innerHTML = `
            <a id="nav-student" class="nav-link" onclick="switchView('student')">
                <i class="fa-solid fa-user-graduate"></i> My Attendance
            </a>
        `;
    }
}

// ================= API FETCH UTILITIES =================

async function fetchAPI(url, options = {}) {
    showSpinner(true);
    
    // Add Bearer Token if logged in
    const headers = options.headers || {};
    if (state.token) {
        headers["Authorization"] = "Bearer " + state.token;
    }
    
    if (options.body && !(options.body instanceof FormData)) {
        headers["Content-Type"] = "application/json";
    }
    
    options.headers = headers;
    
    try {
        const response = await fetch(url, options);
        showSpinner(false);
        
        if (response.status === 401 || response.status === 403) {
            // Unauthorized / Expired Token
            showToast("Authentication Error", "Session expired or access denied.", "danger");
            showAuth();
            return null;
        }
        
        if (!response.ok) {
            const errorMsg = await response.text();
            throw new Error(errorMsg || "API Request failed");
        }
        
        // Handle empty responses
        const contentType = response.headers.get("content-type");
        if (contentType && contentType.includes("application/json")) {
            return await response.json();
        }
        return await response.text();
    } catch (error) {
        showSpinner(false);
        showToast("Error", error.message, "danger");
        console.error("API error:", error);
        throw error;
    }
}

// ================= LOGIN & REGISTER FLOW =================

async function handleLogin(event) {
    event.preventDefault();
    const usernameInput = document.getElementById("login-username").value;
    const passwordInput = document.getElementById("login-password").value;
    
    try {
        const data = await fetchAPI("/api/auth/login", {
            method: "POST",
            body: JSON.stringify({ username: usernameInput, password: passwordInput })
        });
        
        if (data && data.token) {
            state.token = data.token;
            state.user = {
                id: data.userId,
                username: data.username,
                email: data.email,
                role: data.role
            };
            localStorage.setItem("jwt_token", state.token);
            localStorage.setItem("user_info", JSON.stringify(state.user));
            
            showToast("Success", `Welcome back, ${data.username}!`, "success");
            showDashboard();
            
            // Clear form inputs
            document.getElementById("login-form").reset();
        }
    } catch (error) {
        // Handled by fetchAPI toast
    }
}

async function handleRegister(event) {
    event.preventDefault();
    const usernameInput = document.getElementById("reg-username").value;
    const emailInput = document.getElementById("reg-email").value;
    const passwordInput = document.getElementById("reg-password").value;
    const roleInput = document.getElementById("reg-role").value;
    const rollInput = document.getElementById("reg-roll").value;
    const branchInput = document.getElementById("reg-branch").value;
    const semInput = document.getElementById("reg-semester").value;
    
    try {
        const textResponse = await fetchAPI("/api/auth/register", {
            method: "POST",
            body: JSON.stringify({
                username: usernameInput,
                email: emailInput,
                password: passwordInput,
                role: roleInput,
                rollNumber: rollInput,
                branch: branchInput,
                semester: semInput ? parseInt(semInput) : null
            })
        });
        
        if (textResponse) {
            showToast("Registration Success", "Account created successfully. Please login.", "success");
            switchAuthTab("login");
            document.getElementById("register-form").reset();
        }
    } catch (error) {
        // Handled by fetchAPI toast
    }
}

function handleLogout() {
    showToast("Goodbye", "Logged out successfully", "info");
    showAuth();
}

// ================= ADMIN DASHBOARD FUNCTIONS =================

async function loadAdminData() {
    try {
        const users = await fetchAPI("/api/admin/users");
        const subjects = await fetchAPI("/api/admin/subjects");
        const teachers = await fetchAPI("/api/admin/teachers");
        const students = await fetchAPI("/api/admin/students");
        
        if (users) state.admin.users = users;
        if (subjects) state.admin.subjects = subjects;
        if (teachers) state.admin.teachers = teachers;
        if (students) state.admin.students = students;
        
        renderAdminTables();
        populateAdminDropdowns();
    } catch (error) {
        console.error("Failed to load admin data:", error);
    }
}

function renderAdminTables() {
    // Render Users Table
    const usersTable = document.getElementById("admin-users-table");
    usersTable.innerHTML = "";
    state.admin.users.forEach(user => {
        let roleBadge = "badge-info";
        if (user.role === "ROLE_ADMIN") roleBadge = "badge-danger";
        if (user.role === "ROLE_TEACHER") roleBadge = "badge-warning";
        if (user.role === "ROLE_STUDENT") roleBadge = "badge-success";

        usersTable.innerHTML += `
            <tr>
                <td>${user.id}</td>
                <td>${user.rollNumber ? user.rollNumber : '-'}</td>
                <td>${user.branch ? user.branch : '-'}</td>
                <td>${user.semester ? user.semester : '-'}</td>
                <td><strong>${user.username}</strong></td>
                <td>${user.email}</td>
                <td><span class="badge ${roleBadge}">${getRoleLabel(user.role)}</span></td>
            </tr>
        `;
    });
    
    // Render Subjects Table
    const subjectsTable = document.getElementById("admin-subjects-table");
    subjectsTable.innerHTML = "";
    state.admin.subjects.forEach(sub => {
        subjectsTable.innerHTML += `
            <tr>
                <td>${sub.id}</td>
                <td><strong>${sub.name}</strong></td>
                <td>${sub.teacher.username}</td>
                <td><span class="badge badge-info">${sub.students ? sub.students.length : 0} enrolled</span></td>
            </tr>
        `;
    });
}

function populateAdminDropdowns() {
    // Teacher select dropdown
    const teacherSelect = document.getElementById("admin-s-teacher");
    teacherSelect.innerHTML = '<option value="">Select Teacher...</option>';
    state.admin.teachers.forEach(t => {
        teacherSelect.innerHTML += `<option value="${t.id}">${t.username} (${t.email})</option>`;
    });
    
    // Subjects list dropdown for enrollment
    const subjectSelect = document.getElementById("admin-e-subject");
    subjectSelect.innerHTML = '<option value="">Select Subject...</option>';
    state.admin.subjects.forEach(s => {
        subjectSelect.innerHTML += `<option value="${s.id}">${s.name} (Taught by: ${s.teacher.username})</option>`;
    });
    
    // Students list dropdown for enrollment
    const studentSelect = document.getElementById("admin-e-student");
    studentSelect.innerHTML = '<option value="">Select Student...</option>';
    state.admin.students.forEach(st => {
        studentSelect.innerHTML += `<option value="${st.id}">${st.username} (${st.email})</option>`;
    });
}

async function adminCreateUser(event) {
    event.preventDefault();
    const usernameInput = document.getElementById("admin-u-username").value;
    const emailInput = document.getElementById("admin-u-email").value;
    const passwordInput = document.getElementById("admin-u-password").value;
    const roleInput = document.getElementById("admin-u-role").value;
    const rollInput = document.getElementById("admin-u-roll").value;
    const branchInput = document.getElementById("admin-u-branch").value;
    const semInput = document.getElementById("admin-u-semester").value;
    
    try {
        const newUser = await fetchAPI("/api/admin/users", {
            method: "POST",
            body: JSON.stringify({
                username: usernameInput,
                email: emailInput,
                password: passwordInput,
                role: roleInput,
                rollNumber: rollInput,
                branch: branchInput,
                semester: semInput ? parseInt(semInput) : null
            })
        });
        
        if (newUser) {
            showToast("Success", `User ${newUser.username} created successfully!`, "success");
            document.getElementById("admin-create-user-form").reset();
            loadAdminData(); // Reload stats
        }
    } catch (e) {}
}

async function adminCreateSubject(event) {
    event.preventDefault();
    const nameInput = document.getElementById("admin-s-name").value;
    const teacherIdInput = document.getElementById("admin-s-teacher").value;
    const limitInput = document.getElementById("admin-s-limit").value;
    
    try {
        const newSub = await fetchAPI("/api/admin/subjects", {
            method: "POST",
            body: JSON.stringify({
                name: nameInput,
                teacherId: teacherIdInput,
                attendanceLimit: parseFloat(limitInput)
            })
        });
        
        if (newSub) {
            showToast("Success", `Subject ${newSub.name} created successfully!`, "success");
            document.getElementById("admin-create-subject-form").reset();
            loadAdminData(); // Reload stats
        }
    } catch (e) {}
}

async function adminEnrollStudent(event) {
    event.preventDefault();
    const subjectId = document.getElementById("admin-e-subject").value;
    const studentId = document.getElementById("admin-e-student").value;
    
    try {
        const result = await fetchAPI(`/api/admin/subjects/${subjectId}/enroll?studentId=${studentId}`, {
            method: "POST"
        });
        
        if (result) {
            showToast("Enrolled", "Student enrolled in subject successfully", "success");
            document.getElementById("admin-enroll-form").reset();
            loadAdminData();
        }
    } catch (e) {}
}

// ================= TEACHER DASHBOARD FUNCTIONS =================

async function loadTeacherSubjects() {
    try {
        const mySubjects = await fetchAPI("/api/teacher/subjects");
        if (mySubjects) {
            state.teacher.subjects = mySubjects;
            
            const subSelect = document.getElementById("teacher-subject-select");
            subSelect.innerHTML = '<option value="">Choose subject...</option>';
            mySubjects.forEach(sub => {
                subSelect.innerHTML += `<option value="${sub.id}">${sub.name}</option>`;
            });
            
            // Hide dashboard content panel initially until subject is selected
            document.getElementById("teacher-content-panel").classList.add("hidden");
            document.getElementById("teacher-limit-controls").classList.add("hidden");
            state.teacher.activeSubjectId = null;
        }
    } catch (e) {}
}

async function loadTeacherSubjectData() {
    const subjectId = document.getElementById("teacher-subject-select").value;
    if (!subjectId) {
        document.getElementById("teacher-content-panel").classList.add("hidden");
        document.getElementById("teacher-limit-controls").classList.add("hidden");
        state.teacher.activeSubjectId = null;
        return;
    }
    
    state.teacher.activeSubjectId = subjectId;
    document.getElementById("teacher-content-panel").classList.remove("hidden");
    document.getElementById("teacher-limit-controls").classList.remove("hidden");
    
    try {
        // Fetch Statistics and students enrolled
        const stats = await fetchAPI(`/api/teacher/subjects/${subjectId}/statistics`);
        if (stats) {
            document.getElementById("current-limit-val").innerText = stats.attendanceLimit + "%";
            document.getElementById("new-limit-input").value = stats.attendanceLimit;
            
            // Stat Cards
            document.getElementById("stat-total-students").innerText = stats.students.length;
            const lowCount = stats.students.filter(s => s.belowLimit).length;
            document.getElementById("stat-low-attendance").innerText = lowCount;
            
            state.teacher.studentsStats = stats.students;
            
            // Render statistics tables
            renderTeacherStudentStatsTable(stats.students);
            
            // Render mark attendance students checkboxes
            renderMarkAttendanceList(stats.students);
        }
        
        // Fetch all students in system for dropdown list (enrolling existing)
        const systemStudents = await fetchAPI("/api/teacher/students");
        if (systemStudents) {
            const selectEnroll = document.getElementById("teacher-student-enroll-select");
            selectEnroll.innerHTML = '<option value="">Select student...</option>';
            systemStudents.forEach(student => {
                // If student is not already enrolled in this class
                const alreadyEnrolled = stats.students.some(s => s.studentId === student.id);
                if (!alreadyEnrolled) {
                    selectEnroll.innerHTML += `<option value="${student.id}">${student.username} (${student.email})</option>`;
                }
            });
        }
        
    } catch (e) {}
}

function renderTeacherStudentStatsTable(students) {
    const statsTable = document.getElementById("teacher-student-stats-table");
    statsTable.innerHTML = "";
    
    if (students.length === 0) {
        statsTable.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted);">No students enrolled in this subject.</td></tr>`;
        return;
    }
    
    students.forEach(st => {
        const percClass = st.belowLimit ? "badge-danger" : "badge-success";
        const statusLabel = st.belowLimit ? "Below Limit" : (st.totalClasses === 0 ? "No classes" : "Good");
        const badgeClass = st.belowLimit ? "badge-danger" : (st.totalClasses === 0 ? "badge-info" : "badge-success");
        
        statsTable.innerHTML += `
            <tr>
                <td>${st.rollNumber ? st.rollNumber : '-'}</td>
                <td>${st.branch ? st.branch : '-'}</td>
                <td>${st.semester ? st.semester : '-'}</td>
                <td><strong>${st.username}</strong><br><small style="color: var(--text-muted);">${st.email}</small></td>
                <td>${st.totalClasses}</td>
                <td>${st.presentClasses}</td>
                <td><span class="badge ${percClass}">${st.attendancePercentage.toFixed(1)}%</span></td>
                <td><span class="badge ${badgeClass}">${statusLabel}</span></td>
            </tr>
        `;
    });
}

function renderMarkAttendanceList(students) {
    const markList = document.getElementById("student-attendance-list");
    markList.innerHTML = "";
    
    if (students.length === 0) {
        markList.innerHTML = `<div style="text-align: center; color: var(--text-muted); padding: 20px;">Add students to mark attendance.</div>`;
        return;
    }
    
    students.forEach(st => {
        markList.innerHTML += `
            <div class="student-mark-item">
                <div class="student-info-mini">
                    <span class="student-name-mini">${st.username}</span>
                    <span class="student-email-mini">${st.email}</span>
                </div>
                <div class="switch-container">
                    <span id="switch-state-${st.studentId}" class="switch-label-state present">PRESENT</span>
                    <label class="switch">
                        <input type="checkbox" id="check-${st.studentId}" checked onchange="toggleSwitchText(${st.studentId})">
                        <span class="slider"></span>
                    </label>
                </div>
            </div>
        `;
    });
}

function toggleSwitchText(studentId) {
    const checkbox = document.getElementById(`check-${studentId}`);
    const stateText = document.getElementById(`switch-state-${studentId}`);
    
    if (checkbox.checked) {
        stateText.innerText = "PRESENT";
        stateText.className = "switch-label-state present";
    } else {
        stateText.innerText = "ABSENT";
        stateText.className = "switch-label-state absent";
    }
}

async function updateSubjectLimit() {
    const subjectId = state.teacher.activeSubjectId;
    const newLimit = document.getElementById("new-limit-input").value;
    
    if (!newLimit || newLimit < 0 || newLimit > 100) {
        showToast("Error", "Please input a valid percentage between 0 and 100", "danger");
        return;
    }
    
    try {
        const text = await fetchAPI(`/api/teacher/subjects/${subjectId}/limit?limit=${newLimit}`, {
            method: "PUT"
        });
        if (text) {
            showToast("Limit Updated", `Subject threshold is now set to ${newLimit}%`, "success");
            loadTeacherSubjectData(); // Reload statistics
        }
    } catch (e) {}
}

async function submitAttendance(event) {
    event.preventDefault();
    const subjectId = state.teacher.activeSubjectId;
    const dateValue = document.getElementById("attendance-date").value;
    
    if (!dateValue) {
        showToast("Error", "Please select a valid date", "danger");
        return;
    }
    
    // Build Attendance payload map
    const attendanceMap = {};
    state.teacher.studentsStats.forEach(st => {
        const checkbox = document.getElementById(`check-${st.studentId}`);
        attendanceMap[st.studentId] = checkbox.checked ? "PRESENT" : "ABSENT";
    });
    
    try {
        const text = await fetchAPI(`/api/teacher/subjects/${subjectId}/attendance`, {
            method: "POST",
            body: JSON.stringify({
                date: dateValue,
                attendance: attendanceMap
            })
        });
        
        if (text) {
            showToast("Attendance Marked", "Logs submitted successfully", "success");
            loadTeacherSubjectData(); // Refresh graphs/stats
        }
    } catch (e) {}
}

function switchTeacherEnrollTab(tab) {
    const btnEnroll = document.getElementById("btn-sub-enroll");
    const btnCreate = document.getElementById("btn-sub-create");
    const enrollPanel = document.getElementById("teacher-enroll-existing-panel");
    const createPanel = document.getElementById("teacher-create-student-panel");
    
    if (tab === "enroll") {
        btnEnroll.classList.add("active");
        btnCreate.classList.remove("active");
        enrollPanel.classList.remove("hidden");
        createPanel.classList.add("hidden");
    } else {
        btnEnroll.classList.remove("active");
        btnCreate.classList.add("active");
        enrollPanel.classList.add("hidden");
        createPanel.classList.remove("hidden");
    }
}

async function teacherEnrollStudent(event) {
    event.preventDefault();
    const subjectId = state.teacher.activeSubjectId;
    const studentId = document.getElementById("teacher-student-enroll-select").value;
    
    if (!studentId) {
        showToast("Error", "Please select a student", "danger");
        return;
    }
    
    try {
        const result = await fetchAPI(`/api/teacher/subjects/${subjectId}/enroll?studentId=${studentId}`, {
            method: "POST"
        });
        
        if (result) {
            showToast("Enrolled", "Student enrolled in subject successfully", "success");
            loadTeacherSubjectData();
        }
    } catch (e) {}
}

async function teacherCreateAndEnrollStudent(event) {
    event.preventDefault();
    const subjectId = state.teacher.activeSubjectId;
    const rollVal = document.getElementById("teacher-u-roll").value;
    const branchVal = document.getElementById("teacher-u-branch").value;
    const semVal = document.getElementById("teacher-u-semester").value;
    const usernameVal = document.getElementById("teacher-u-username").value;
    const emailVal = document.getElementById("teacher-u-email").value;
    const passwordVal = document.getElementById("teacher-u-password").value;
    
    try {
        // Step 1: Create Student Account
        const student = await fetchAPI("/api/teacher/students", {
            method: "POST",
            body: JSON.stringify({
                username: usernameVal,
                email: emailVal,
                password: passwordVal,
                role: "ROLE_STUDENT",
                rollNumber: rollVal,
                branch: branchVal,
                semester: semVal ? parseInt(semVal) : null
            })
        });
        
        if (student) {
            // Step 2: Enroll in subject
            const enrollResult = await fetchAPI(`/api/teacher/subjects/${subjectId}/enroll?studentId=${student.id}`, {
                method: "POST"
            });
            
            if (enrollResult) {
                showToast("Success", `Student account ${student.username} created & enrolled!`, "success");
                document.getElementById("teacher-create-student-form").reset();
                loadTeacherSubjectData();
            }
        }
    } catch (e) {}
}

async function downloadPdfReport() {
    const subjectId = state.teacher.activeSubjectId;
    const subjectName = state.teacher.subjects.find(s => s.id == subjectId).name;
    
    showSpinner(true);
    try {
        const response = await fetch(`/api/teacher/subjects/${subjectId}/report`, {
            headers: {
                "Authorization": "Bearer " + state.token
            }
        });
        showSpinner(false);
        
        if (!response.ok) throw new Error("Could not download report PDF");
        
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = `attendance_report_${subjectName.replace(/\s+/g, "_")}.pdf`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        
        showToast("Downloaded", "PDF generated and downloaded successfully", "success");
    } catch (error) {
        showSpinner(false);
        showToast("Download Error", error.message, "danger");
    }
}

// ================= STUDENT DASHBOARD FUNCTIONS =================

async function loadStudentAttendance() {
    try {
        const records = await fetchAPI("/api/student/attendance");
        if (records) {
            const table = document.getElementById("student-attendance-table");
            table.innerHTML = "";
            
            if (records.length === 0) {
                table.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-muted);">You are not currently enrolled in any subjects.</td></tr>`;
                return;
            }
            
            records.forEach(rec => {
                const badgeClass = rec.belowLimit ? "badge-danger" : "badge-success";
                const badgeLabel = rec.belowLimit ? "Below Limit" : "Good";
                const cellPercClass = rec.belowLimit ? "badge-danger" : "badge-success";
                
                table.innerHTML += `
                    <tr>
                        <td><strong>${rec.subjectName}</strong></td>
                        <td>${rec.teacherName}</td>
                        <td>${rec.totalClasses}</td>
                        <td>${rec.presentClasses}</td>
                        <td>${rec.attendanceLimit}%</td>
                        <td><span class="badge ${cellPercClass}">${rec.attendancePercentage.toFixed(1)}%</span></td>
                        <td><span class="badge ${badgeClass}">${badgeLabel}</span></td>
                    </tr>
                `;
            });
        }
    } catch (e) {}
}

// ================= WEBSOCKET STOMP REAL-TIME UPDATES =================

function initializeWebSocket() {
    if (state.stompClient && state.stompClient.connected) {
        return; // Already connected
    }
    
    // Create connection endpoint via SockJS fallback
    const socket = new SockJS("/ws");
    state.stompClient = Stomp.over(socket);
    
    // Suppress debug logs in console to keep clean
    state.stompClient.debug = null;
    
    state.stompClient.connect({}, (frame) => {
        // Connection success callback
        if (state.user.role === "ROLE_STUDENT") {
            // Subscribe to student specific attendance channel
            state.stompClient.subscribe("/topic/attendance/" + state.user.id, (message) => {
                if (message.body) {
                    const payload = JSON.parse(message.body);
                    
                    // Show a real-time floating Toast alert
                    const statusStr = payload.status === "PRESENT" ? "PRESENT 🟢" : "ABSENT 🔴";
                    showToast(
                        "Attendance Marked!",
                        `You were marked <strong>${statusStr}</strong> in <strong>${payload.subjectName}</strong>. Current percentage: ${payload.percentage.toFixed(1)}%`,
                        payload.percentage < 75.0 ? "warning" : "success",
                        10000 // Show for 10 seconds since it's important
                    );
                    
                    // Refresh current student view in the background if active
                    if (state.activeView === "student") {
                        loadStudentAttendance();
                    }
                }
            });
        }
    }, (error) => {
        console.warn("STOMP connection failed. Reconnecting in 5 seconds...", error);
        setTimeout(initializeWebSocket, 5000);
    });
}

// ================= UI HELPERS =================

function showSpinner(show) {
    const spinner = document.getElementById("spinner");
    if (spinner) {
        if (show) spinner.classList.add("active");
        else spinner.classList.remove("active");
    }
}

function showToast(title, message, type = "success", duration = 4000) {
    const container = document.getElementById("toast-container");
    if (!container) return;
    
    const toast = document.createElement("div");
    toast.className = `toast`;
    if (type === "danger") toast.style.borderColor = "var(--accent-danger)";
    if (type === "warning") toast.style.borderColor = "var(--accent-warning)";
    if (type === "info") toast.style.borderColor = "var(--primary-color)";
    if (type === "success") toast.style.borderColor = "var(--accent-success)";
    
    let icon = "fa-solid fa-circle-check";
    if (type === "danger") icon = "fa-solid fa-circle-xmark";
    if (type === "warning") icon = "fa-solid fa-circle-exclamation";
    if (type === "info") icon = "fa-solid fa-circle-info";
    
    toast.innerHTML = `
        <i class="${icon} toast-icon" style="color: ${type === "danger" ? "var(--accent-danger)" : (type === "warning" ? "var(--accent-warning)" : (type === "info" ? "var(--primary-color)" : "var(--accent-success)"))}"></i>
        <div class="toast-content">
            <h4>${title}</h4>
            <p>${message}</p>
        </div>
    `;
    
    container.appendChild(toast);
    
    // Auto remove
    setTimeout(() => {
        toast.classList.add("hide");
        setTimeout(() => {
            toast.remove();
        }, 300);
    }, duration);
}

async function loadMentorData() {
    try {
        const data = await fetchAPI("/api/teacher/mentor/overall-statistics");
        if (data) {
            const table = document.getElementById("mentor-overall-table");
            table.innerHTML = "";

            if (data.length === 0) {
                table.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-muted);">No student accounts in system.</td></tr>`;
                return;
            }

            data.forEach(st => {
                let breakdownHtml = "";
                if (st.subjectBreakdown.length === 0) {
                    breakdownHtml = `<em style="color: var(--text-muted);">Not enrolled in any subjects</em>`;
                } else {
                    st.subjectBreakdown.forEach(sub => {
                        const badgeClass = sub.belowLimit ? "badge-danger" : "badge-success";
                        breakdownHtml += `
                            <span class="badge ${badgeClass}" style="margin: 2px; text-transform: none;">
                                ${sub.subjectName}: ${sub.percentage.toFixed(1)}%
                            </span>
                        `;
                    });
                }

                const overallBadgeClass = st.hasWarning ? "badge-danger" : "badge-success";
                const overallBadgeLabel = st.hasWarning ? "Warning" : "Good";

                table.innerHTML += `
                    <tr>
                        <td>${st.rollNumber ? st.rollNumber : '-'}</td>
                        <td>${st.branch ? st.branch : '-'}</td>
                        <td>${st.semester ? st.semester : '-'}</td>
                        <td><strong>${st.username}</strong></td>
                        <td>${st.email}</td>
                        <td style="max-width: 320px; line-height: 1.6;">${breakdownHtml}</td>
                        <td>${st.totalClasses}</td>
                        <td>${st.presentClasses}</td>
                        <td><span class="badge ${overallBadgeClass}">${st.overallPercentage.toFixed(1)}%</span></td>
                        <td><span class="badge ${overallBadgeClass}">${overallBadgeLabel}</span></td>
                    </tr>
                `;
            });
        }
    } catch (e) {}
}

async function downloadOverallMentorReport() {
    showSpinner(true);
    try {
        const response = await fetch("/api/teacher/mentor/overall-report", {
            headers: {
                "Authorization": "Bearer " + state.token
            }
        });
        showSpinner(false);

        if (!response.ok) throw new Error("Could not download consolidated report");

        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = `overall_mentor_attendance_report.pdf`;
        document.body.appendChild(a);
        a.click();
        a.remove();

        showToast("Downloaded", "Consolidated mentor PDF report downloaded successfully", "success");
    } catch (error) {
        showSpinner(false);
        showToast("Download Error", error.message, "danger");
    }
}
