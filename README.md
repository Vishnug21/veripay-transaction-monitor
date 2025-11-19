# Veripay Transaction Monitor

Veripay Transaction Monitor is an Android application designed to help store managers **automatically verify UPI payments**, **track real-time transactions**, and **securely manage store activity** using SMS parsing and Firebase Firestore.  
The app monitors incoming SMS messages for UPI confirmations and instantly updates payment status inside the dashboard.

---

## 📱 Screenshots

### **1️⃣ Login Page**
![Login Page](https://github.com/user-attachments/assets/311d6c8a-7737-47af-801c-0568540ccb0b)

---

### **2️⃣ Home Page (After Login)**
Displays:
- Ledger balance  
- Today’s total  
- All-time total  
- Last 3 transactions  
- Quick button to view all transactions  

![Home Page](https://github.com/user-attachments/assets/562de51b-c97a-4723-8617-d029fd1d0ea1)

---

### **3️⃣ Dashboard (All Transactions)**
Shows every transaction linked to the store:
- Amount  
- Status (SUCCESS / PENDING)  
- Timestamp  
- Transaction ID  

![Dashboard](https://github.com/user-attachments/assets/c1f3a0f5-0f90-49b1-8e19-78553ed5889a)

---

### **4️⃣ Payment Verification Screen**
Used to manually verify any transaction by entering Transaction ID / Amount.

![Payment Verification](https://github.com/user-attachments/assets/b4ccc17d-fa70-4587-b918-6f45b23b28eb)

---

### **5️⃣ Payment Verified – Confirmation Popup**
When a matching SMS is found or the transaction is validated against Firestore.

![Payment Verified](https://github.com/user-attachments/assets/e210c528-f047-44a0-88d2-64ee34db02d0)

---

### **6️⃣ Settings Screen**
Includes:
- Email linked to account  
- Enable SMS Permission  
- Notification Access  
- Logout  
- Seed 15 test transactions button  

![Settings Page](https://github.com/user-attachments/assets/ec180c22-340d-4fd3-b0d1-8e528cbdc75e)

---

## 🚀 Features

### ✔ **Automatic SMS-Based Payment Verification**
The app reads UPI payment confirmation SMS messages and updates Firestore instantly.

### ✔ **Dashboard with Real-Time Updates**
Displays all transactions for the store with status, amount, and timestamp.

### ✔ **Sorting & Filtering Options**
Sort by:
- Date  
- Amount  
- Status  

### ✔ **Daily & All-Time Ledger**
The Home screen shows:
- Total balance received today  
- All-time lifetime earnings  

### ✔ **Manual Payment Verification**
Enter the transaction ID to verify manually.

### ✔ **Firestore Integration**
Transactions are securely stored in Firebase Firestore with strict security rules:
- Users can only access their own store transactions  
- No cross-store access  

### ✔ **Test Transaction Seeder**
Generates 15 sample transactions for testing:
- 13 SUCCESS  
- 2 PENDING  

### ✔ **Login & Registration**
Authenticated with Firebase Authentication.

---

## 🛠️ Tech Stack

- **Android Studio (Kotlin)**
- **Firebase Authentication**
- **Firebase Firestore**
- **Firebase Firestore Security Rules**
- **SMS Manager / Notification Listener**
- **RecyclerView + Adapters**
- **MVVM-like architecture (lightweight)**

---

## 🔧 Firebase Structure

### **users → {uid}**
| Field | Type | Description |
|-------|------|-------------|
| storeId | String | The store associated with the user |
| email | String | Login email |
| password | String | Auth password |

### **transactions → {transactionId}**
| Field | Type | Description |
|-------|------|-------------|
| amount | Number | Transaction amount |
| reference | String | UPI reference ID |
| status | SUCCESS / PENDING | Transaction status |
| timestamp | Firestore Timestamp | Transaction date |
| matchedBy | String | SMS / Manual |
| storeId | String | Store associated with the user |

---

## 🔐 Firestore Rules Summary

- Users can **only read/write their own store's transactions**.
- No user can access another store's data.
- Test data seeding allowed only for authenticated users.

---

## 📲 Installation Instructions

### **Option 1 — Install APK (Debug)**
1. Download `app-debug.apk` from the GitHub Releases section  
2. Install the APK  
3. Allow **Install unknown apps** if prompted  

### **Option 2 — Build inside Android Studio**
1. Clone the repo  
2. Open in Android Studio  
3. Connect phone or run emulator  
4. Click **Run ▶**

---

## 🧪 Testing the App

### **Automatic Verification via SMS**
Send an SMS to emulator using:

```sh
adb emu sms send 12345 "GPay: ₹500 sent to Veripay. UPI Ref: REF005. Txn: TXN005"
