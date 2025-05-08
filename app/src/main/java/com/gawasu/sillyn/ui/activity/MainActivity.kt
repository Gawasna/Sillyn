package com.gawasu.sillyn.ui.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.bumptech.glide.Glide
import com.gawasu.sillyn.R
import com.gawasu.sillyn.databinding.ActivityMainBinding
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
    private lateinit var navController: NavController // Thêm NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        // Lấy NavController từ NavHostFragment
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navController = navHostFragment.navController

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView

        // Định nghĩa các destination cấp cao nhất (top-level destinations)
        // Navigation Component sẽ hiển thị biểu tượng Drawer (hamburger icon) cho các destination này
        // Thay vì mũi tên Back.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                // IDs của các item trong BottomNavigationView
                R.id.taskFragment, // ID của Task Fragment trong nav_graph
                R.id.calendarFragment, // ID của Calendar Fragment trong nav_graph
                R.id.settingsFragment, // ID của Settings Fragment trong nav_graph
                // Có thể thêm IDs của các mục Drawer cấp cao nhất nếu chúng không dẫn đến TaskFragment
                // (ví dụ: một trang "About" độc lập)
                // Nếu các mục Drawer cố định dẫn đến TaskFragment với filter khác nhau,
                // chỉ cần taskFragment là top-level destination.
            ), drawerLayout
        )

        // Kết nối Toolbar với NavController và AppBarConfiguration
        // Điều này xử lý tiêu đề trên Toolbar và nút Drawer/Back
        setupActionBarWithNavController(navController, appBarConfiguration)

        // Kết nối NavigationView (Drawer) với NavController
        // Điều này xử lý việc tự động navigate khi click vào item Drawer
        // (nếu ID item Drawer trùng với ID destination trong graph)
        // Tuy nhiên, với dynamic items và passing arguments, chúng ta sẽ cần xử lý thủ công.
        // Chỉ cần thiết lập cho các item cố định nếu ID trùng khớp.
        // navView.setupWithNavController(navController) // Có thể bỏ qua nếu xử lý click thủ công

        // Kết nối BottomNavigationView với NavController
        // Điều này xử lý việc tự động navigate khi click vào item Bottom Nav
        binding.bottomNavigation.setupWithNavController(navController)


        auth = FirebaseAuth.getInstance()

        setupNavHeader()
        setupLogoutButton()
        setupDrawerMenuItems() // Cập nhật tên hàm xử lý menu Drawer
        deb()

        checkLoginStatus()

        observeTaskCategories()

        // Không cần loadFragment(TaskFragment()) thủ công nữa
        // Navigation Component sẽ load app:startDestination (@id/taskFragment) tự động
    }

    private fun deb() {
        mainViewModel.check()
        Log.i(TAG, "CHECK")
    }

    private fun checkLoginStatus() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.i(TAG, "User not logged in in MainActivity, navigating to AuthenticationActivity")
            navigateToAuthenticationActivity()
        } else {
            Log.i(TAG, "User logged in, loading MainActivity content")
            // Ensure user data is loaded for nav header
            setupNavHeader()
        }
    }

    // Không cần hàm loadFragment thủ công nữa
    /*
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
    */

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

    // Cập nhật hàm xử lý click menu Drawer
    private fun setupDrawerMenuItems() {
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            // Đóng drawer trước khi navigate
            binding.drawerLayout.closeDrawer(binding.navView)

            val bundle = Bundle()
            bundle.putString("categoryName", null)

            when (menuItem.itemId) {
                R.id.nav_inbox_tasks -> {
                    Log.d(TAG, "Drawer: Inbox Tasks clicked")
                    bundle.putString("filterType", "inbox")
                    navController.navigate(R.id.taskFragment, bundle)
                }
                R.id.nav_today_tasks -> {
                    Log.d(TAG, "Drawer: Today Tasks clicked")
                    bundle.putString("filterType", "today")
                    navController.navigate(R.id.taskFragment, bundle)
                }
                R.id.nav_week_task -> {
                    Log.d(TAG, "Drawer: Week Tasks clicked")
                    bundle.putString("filterType", "week")
                    navController.navigate(R.id.taskFragment, bundle)
                }
                // Dynamic items will be handled in updateDynamicMenuItems listener
                else -> {
                    // Các mục cố định khác nếu có
                    Log.d(TAG, "Drawer: Unhandled menu item clicked: ${menuItem.title}")
                    false
                }
            }
            true // Báo hiệu sự kiện đã được xử lý cho các item được xử lý tại đây
        }
    }

    // Bỏ hoặc xoá hàm setBottomItem() thủ công
    /*
    private fun setBottomItem() {
        // Already handled by setupWithNavController(navController)
    }
    */

    // Cập nhật hàm updateDynamicMenuItems để xử lý click với Navigation Component
    private fun updateDynamicMenuItems(categories: List<String>) {
        val navMenu = binding.navView.menu
        navMenu.removeGroup(R.id.menu_categories_group)

        categories.forEachIndexed { index, categoryName ->
            // Tạo một ID duy nhất cho mỗi item động
            // Có thể lưu category ID thực tế nếu bạn có nó, ví dụ: category.id
            // Để đơn giản, ta dùng index + một offset lớn để tránh trùng với ID cố định
            val dynamicItemId = Menu.FIRST + 100 + index // Offset để tránh trùng nav_inbox_tasks, v.v.

            val newItem = navMenu.add(
                R.id.menu_categories_group,
                dynamicItemId, // Sử dụng ID động
                Menu.NONE,
                categoryName
            )
            newItem.setIcon(R.drawable.baseline_calendar_today_24) // Thay đổi icon nếu cần

            // Xử lý khi người dùng chọn danh mục động
            newItem.setOnMenuItemClickListener {
                Log.d(TAG, "Drawer: Clicked category: $categoryName (ID: $dynamicItemId)")

                // Chuẩn bị arguments để gửi đến TaskFragment
                val bundle = Bundle()
                // Giả sử categoryName có thể dùng để lọc, hoặc bạn có category ID thực tế
                // Nếu bạn có category ID, hãy truyền nó thay vì tên
                // bundle.putString("categoryId", actualCategoryId)
                bundle.putString("filterType", "category") // Gán loại filter là 'category'
                bundle.putString("categoryName", categoryName) // Truyền tên danh mục (hoặc ID thực)

                // Navigate đến TaskFragment với arguments
                navController.navigate(R.id.taskFragment, bundle)

                binding.drawerLayout.closeDrawer(binding.navView)
                true // Báo hiệu sự kiện đã được xử lý
            }
        }
        Log.d(TAG, "Dynamic categories menu updated with ${categories.size} items.")
        // Cập nhật lại NavigationView với NavController sau khi thay đổi menu
        // (Mặc dù setupWithNavController() ban đầu đã đủ kết nối cơ bản)
        // binding.navView.setupWithNavController(navController) // Thường không cần thiết sau khi gọi onCreate
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
                    Toast.makeText(this, "Không thể load danh mục", Toast.LENGTH_SHORT).show()
                }
                is FirebaseResult.Loading -> {
                    // Hiển thị loading indicator nếu cần
                }
            }
        }
    }

    private fun navigateToAuthenticationActivity() {
        startActivity(Intent(this, AuthenticationActivity::class.java))
        finish()
    }

    // Bắt sự kiện Up/Back từ AppBar để Navigation Component xử lý
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    // Inflate menu options (Search, Settings button on AppBar)
//    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
//        menuInflater.inflate(R.menu.main_menu, menu)
//        return true // Sử dụng true để hiển thị menu
//    }

    // Xử lý sự kiện click trên menu options (ví dụ nút search trên AppBar)
    /*
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Let Navigation Component handle clicks on top-level destinations (if they exist in options menu)
        // or if actions are defined to navigate from the current destination
        return item.onNavDestinationSelected(navController) || super.onOptionsItemSelected(item)
    }
    */


    companion object {
        private const val TAG = "MAIN ACTIVITY"
    }
}