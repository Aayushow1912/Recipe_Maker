package com.example.recipeapp

import android.annotation.SuppressLint
import android.inputmethodservice.InputMethodService
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.Room
import com.example.recipeapp.databinding.ActivityHomeBinding
import com.example.recipeapp.databinding.ActivitySearchBinding

class SearchActivity : AppCompatActivity() {
    private lateinit var binding:ActivitySearchBinding
    private lateinit var rvAdapter:SearchAdapter
    private lateinit var datList:ArrayList<Recipe>
    private lateinit var recipes: List<Recipe?>
    @SuppressLint("ServiceCast")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding=ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.search.requestFocus()
        var db = Room.databaseBuilder(this@SearchActivity, AppDatabase::class.java, "db_name")
            .allowMainThreadQueries()
            .fallbackToDestructiveMigration()
            .createFromAsset("recipe.db")
            .build()
        var daoObject = db.getDao()
        recipes = daoObject.getAll()
        setUpRecyclerView()
        binding.goBackHome.setOnClickListener{
            finish()
        }
        binding.search.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                if (p0.toString() != "") {
                    filterData(p0.toString())
                } else {
                    setUpRecyclerView()
                }
            }

            override fun afterTextChanged(p0: Editable?) {

            }
        })
    }

    private fun filterData(filterText: String) {
        var filterData=ArrayList<Recipe>()
        for(i in recipes.indices){
            if(recipes[i]!!.tittle.lowercase().contains(filterText.lowercase())){
                filterData.add(recipes[i]!!)
            }
            rvAdapter.filterList(filterList = filterData)
        }
    }

    private fun setUpRecyclerView() {
        datList = ArrayList()
        binding.rvSearch.layoutManager =
            LinearLayoutManager(this)
        for (i in recipes!!.indices) {
            if (recipes[i]!!.category.contains("Popular")) {
                datList.add(recipes[i]!!)
            }
        }
        Log.v("items are",datList.toString())
        rvAdapter = SearchAdapter(datList, this)
        binding.rvSearch.adapter = rvAdapter
    }
}
