package com.gawasu.sillyn.ui.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.navigation.ui.AppBarConfiguration
import com.bumptech.glide.Glide
import com.gawasu.sillyn.R
import com.gawasu.sillyn.databinding.ActivityMainBinding
import com.gawasu.sillyn.ui.fragment.TaskFragment
import com.gawasu.sillyn.ui.viewmodel.MainViewModel
import com.gawasu.sillyn.utils.FirebaseResult
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_tasks, R.id.nav_calendar,
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
        deb()

        //setBottomItem() //debug

        checkLoginStatus() // Kiểm tra trạng thái đăng nhập ngay khi MainActivity được tạo

        observeTaskCategories()


        // Load TaskFragment into fragment_container - Đảm bảo Fragment chính được load
        if (savedInstanceState == null) {
            loadFragment(TaskFragment())
        }
    }

    private fun deb() {
        mainViewModel.check()
        Log.i(TAG, "CHECK")
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
        val logoutButton: ImageButton = headerView.findViewById(R.id.btnLogout)
        logoutButton.setOnClickListener {
            auth.signOut()
            Log.i(TAG, "Logout successful, navigating to AuthenticationActivity")
            navigateToAuthenticationActivity()
        }
    }

    private fun setupMenuItems() {
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_inbox_tasks -> {
                    // Handle Tasks menu item click
                }
                R.id.nav_today_tasks -> {
                    // Handle Calendar menu item click
                }
                R.id.nav_week_task -> {
                    //
                }
            }
            binding.drawerLayout.closeDrawer(binding.navView)
            true
        }
    }

    private fun setBottomItem() {
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_tasks -> {
                    Log.d(TAG, "Click righ s ss")
                }
            }
            true
        }
    }

    private fun updateDynamicMenuItems(categories: List<String>) {
        val navMenu = binding.navView.menu

        // Xóa tất cả các item hiện có trong group R.id.menu_categories_group
        // Điều này đảm bảo menu được cập nhật và không bị lặp lại các danh mục cũ
        navMenu.removeGroup(R.id.menu_categories_group)

        // Duyệt qua danh sách categories và tạo item cho mỗi danh mục
        categories.forEachIndexed { index, categoryName ->
            // Tạo một item mới. Tham số đầu tiên là group ID mà item này thuộc về.
            val newItem = navMenu.add(
                R.id.menu_categories_group, // Gán item này vào group có ID là menu_categories_group
                Menu.FIRST + index, // ID duy nhất cho item (sử dụng index + offset để đơn giản)
                Menu.NONE, // Thứ tự hiển thị (NONE hoặc một số int để sắp xếp)
                categoryName // Tiêu đề của item
            )
            newItem.setIcon(R.drawable.baseline_calendar_today_24) // Bạn có thể thay đổi icon

            // Xử lý khi người dùng chọn danh mục
            newItem.setOnMenuItemClickListener {
                Toast.makeText(this, "Clicked category: $categoryName", Toast.LENGTH_SHORT).show()
                // TODO: Load fragment hoặc filter task theo category
                binding.drawerLayout.closeDrawer(binding.navView) // Đóng drawer sau khi chọn
                true // Báo hiệu sự kiện đã được xử lý
            }
        }
        Log.d(TAG, "Dynamic categories menu updated with ${categories.size} items.")
    }

    private fun observeTaskCategories() {
        mainViewModel.taskCategories.observe(this) { result ->
            when (result) {
                is FirebaseResult.Success -> {
                    val categories = result.data
                    updateDynamicMenuItems(categories) // Truyền vào danh sách các tên danh mục
                }
                is FirebaseResult.Error -> {
                    Log.e(TAG, "Lỗi khi load categories: ${result.exception}")
                }
                is FirebaseResult.Loading -> {
                    // Có thể hiển thị một loading indicator nếu cần
                }
            }
        }
    }

    private fun navigateToAuthenticationActivity() {
        startActivity(Intent(this, AuthenticationActivity::class.java))
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onSupportNavigateUp(): Boolean {
        return true
    }

    companion object {
        private const val TAG = "MAIN ACTIVITY"
    }
}