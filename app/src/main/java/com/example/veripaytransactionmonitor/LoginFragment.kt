package com.example.veripaytransactionmonitor

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth

class LoginFragment : Fragment() {

    private val auth by lazy { FirebaseAuth.getInstance() }

    private lateinit var etStoreId: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var tvLoginStatus: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etStoreId = view.findViewById(R.id.etStoreId)
        etPassword = view.findViewById(R.id.etPassword)
        btnLogin = view.findViewById(R.id.btnLogin)
        btnRegister = view.findViewById(R.id.btnRegister)
        tvLoginStatus = view.findViewById(R.id.tvLoginStatus)

        // Always clear input fields when arriving at login so user must enter credentials again
        etStoreId.setText("")
        etPassword.setText("")
        tvLoginStatus.visibility = View.GONE

        btnLogin.setOnClickListener {
            performLogin()
        }

        btnRegister.setOnClickListener {
            // Optional: navigate to a register fragment or start register flow
            Toast.makeText(requireContext(), "Register flow not implemented", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performLogin() {
        val storeId = etStoreId.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (TextUtils.isEmpty(storeId) || TextUtils.isEmpty(password)) {
            tvLoginStatus.text = "Please enter store id and password"
            tvLoginStatus.visibility = View.VISIBLE
            return
        }

        tvLoginStatus.visibility = View.GONE

        // --- Firebase email/password example ---
        // If your storeId is an email and you're using FirebaseAuth:
        auth.signInWithEmailAndPassword(storeId, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Login successful: navigate to Home and clear the login fragment from back stack
                    val navOptions = NavOptions.Builder()
                        .setPopUpTo(R.id.loginFragment, true)
                        .build()
                    findNavController().navigate(R.id.action_loginFragment_to_homeFragment, null, navOptions)
                } else {
                    tvLoginStatus.text = task.exception?.localizedMessage ?: "Login failed"
                    tvLoginStatus.visibility = View.VISIBLE
                }
            }

        // --- If you use a custom authentication system (storeId/password) replace above with your auth call ---
    }
}
