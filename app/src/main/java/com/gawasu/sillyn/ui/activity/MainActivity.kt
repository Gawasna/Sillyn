package com.gawasu.sillyn.ui.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.navigation.ui.AppBarConfiguration
import com.bumptech.glide.Glide
import com.gawasu.sillyn.R
import com.gawasu.sillyn.databinding.ActivityMainBinding
import com.gawasu.sillyn.ui.fragment.TaskFragment
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_tasks, R.id.nav_calendar, R.id.nav_categories, R.id.nav_settings
            ), drawerLayout
        )

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, binding.appBarMain.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        auth = FirebaseAuth.getInstance()

        setupNavHeader()
        setupLogoutButton()
        setupMenuItems()

        checkLoginStatus() // Kiểm tra trạng thái đăng nhập ngay khi MainActivity được tạo

        // Load TaskFragment into fragment_container - Đảm bảo Fragment chính được load
        if (savedInstanceState == null) {
            loadFragment(TaskFragment())
        }
    }

    private fun checkLoginStatus() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // Nếu chưa đăng nhập, chuyển hướng đến màn hình đăng nhập
            Log.i(TAG, "User not logged in in MainActivity, navigating to AuthenticationActivity")
            navigateToAuthenticationActivity()
        } else {
            Log.i(TAG, "User logged in, loading MainActivity content")
            // Nếu đã đăng nhập, tiếp tục hiển thị MainActivity
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun setupNavHeader() {
        val headerView = binding.navView.getHeaderView(0)
        val userNameTextView: TextView = headerView.findViewById(R.id.tvUserName)
        val userEmailTextView: TextView = headerView.findViewById(R.id.tvUserEmail)
        val userAvatarImageView: ImageView = headerView.findViewById(R.id.imageView)

        val currentUser = auth.currentUser
        if (currentUser != null) {
            userNameTextView.text = currentUser.displayName ?: currentUser.email?.substringBefore("@") ?: "User Name"
            userEmailTextView.text = currentUser.email ?: "No Email"
            val photoUrl = currentUser.photoUrl
            if (photoUrl != null) {
                Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.mipmap.ic_launcher_round)
                    .error(R.mipmap.ic_launcher_round)
                    .circleCrop()
                    .into(userAvatarImageView)
            } else {
                userAvatarImageView.setImageResource(R.mipmap.ic_launcher_round)
            }
        } else {
            userNameTextView.text = getString(R.string.nav_header_title)
            userEmailTextView.text = getString(R.string.nav_header_subtitle)
            userAvatarImageView.setImageResource(R.mipmap.ic_launcher_round)
        }
    }

    private fun setupLogoutButton() {
        val headerView = binding.navView.getHeaderView(0)
        val logoutButton: Button = headerView.findViewById(R.id.btnLogout)
        logoutButton.setOnClickListener {
            auth.signOut()
            Log.i(TAG, "Logout successful, navigating to AuthenticationActivity")
            navigateToAuthenticationActivity()
        }
    }

    private fun setupMenuItems() {
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_tasks -> {
                    // Handle Tasks menu item click
                }
                R.id.nav_calendar -> {
                    // Handle Calendar menu item click
                }
                R.id.nav_categories -> {
                    // Handle Categories menu item click
                }
                R.id.nav_settings -> {
                    // Handle Settings menu item click
                }
                R.id.nav_share -> {
                    // Handle Share menu item click
                }
                R.id.nav_rate_us -> {
                    // Handle Rate Us menu item click
                }
            }
            binding.drawerLayout.closeDrawer(binding.navView)
            true
        }
    }

    private fun navigateToAuthenticationActivity() {
        startActivity(Intent(this, AuthenticationActivity::class.java))
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        return true
    }

    companion object {
        private const val TAG = "MAIN ACTIVITY"
    }
}