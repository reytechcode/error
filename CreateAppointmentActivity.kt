package com.foxcode.citamedica.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import com.foxcode.citamedica.R
import com.foxcode.citamedica.io.ApiService
import com.foxcode.citamedica.io.response.SimpleResponse
import com.foxcode.citamedica.model.Doctor
import com.foxcode.citamedica.model.Schedule
import com.foxcode.citamedica.model.Specialty
import com.foxcode.citamedica.util.PreferenceHelper
import com.foxcode.citamedica.util.PreferenceHelper.get
import com.google.android.material.snackbar.Snackbar
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Calendar

class CreateAppointmentActivity : AppCompatActivity() {
    private val selectedCalendar = Calendar.getInstance()
    private var selectedRadioButton: RadioButton? = null

    private val apiService: ApiService by lazy {
        ApiService.create()
    }

    private val preferences by lazy {
        PreferenceHelper.defaultPrefs(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_appointment)

        val btnNext = findViewById<Button>(R.id.btn_siguiente)
        val btnNext2 = findViewById<Button>(R.id.btn_siguiente_dos)
        val btnConfirm = findViewById<Button>(R.id.btn_confirmar)
        val cvNext = findViewById<CardView>(R.id.cv_siguiente)
        val cvConfirm = findViewById<CardView>(R.id.cv_confirmar)
        val cvResumen = findViewById<CardView>(R.id.cv_resumen)
        val etDescription = findViewById<EditText>(R.id.et_description)
        val etScheduledDate = findViewById<EditText>(R.id.et_fecha)
        val linearLayoutCreateAppointment = findViewById<LinearLayout>(R.id.linearLayout_create_appointment)

        btnNext.setOnClickListener{
            if (etDescription.text.toString().length < 3){
                etDescription.error = "La descripcion es demasido corta"
            }else {
                cvNext.visibility = View.GONE
                cvConfirm.visibility = View.VISIBLE
            }
        }
        btnNext2.setOnClickListener{
            if (etScheduledDate.text.toString().isEmpty()){
                etScheduledDate.error = ""
                Snackbar.make(linearLayoutCreateAppointment, "Debe escoger una fecha para la cita", Snackbar.LENGTH_SHORT).show()
            }else if (selectedRadioButton == null){
                Snackbar.make(linearLayoutCreateAppointment, "Debe seleccionar una hora para la cita", Snackbar.LENGTH_SHORT).show()

            }else {
                showAppointmentDataToConfirm()
                cvConfirm.visibility = View.GONE
                cvResumen.visibility = View.VISIBLE
            }
        }

        btnConfirm.setOnClickListener{
            performStoreAppointment()
        }

        loadSpecialties()
        listenSpecialtyChanges()
        listenDoctorAndDataChanges()
    }

    private fun performStoreAppointment() {
        val btnConfirm = findViewById<Button>(R.id.btn_confirmar)
        btnConfirm.isClickable = false

        val jwt = preferences["jwt", ""]
        val authorization = "Bearer $jwt"

        val tvConfirmdescription = findViewById<TextView>(R.id.tv_resumen_sintomas)
        val tvConfirmDate = findViewById<TextView>(R.id.tv_resumen_fecha)
        val tvConfirmTime = findViewById<TextView>(R.id.tv_resumen_hora)
        val tvConfirmType = findViewById<TextView>(R.id.tv_resumen_tipoConsulta)
        val spinnerDoctor = findViewById<Spinner>(R.id.spinner_medico)
        val spinnerSpecialities = findViewById<Spinner>(R.id.spinner_especialidades)

        val description = tvConfirmdescription.text.toString()
        val scheduledDate = tvConfirmDate.text.toString()
        val scheduledTime = tvConfirmTime.text.toString()
        val type = tvConfirmType.text.toString()
        val doctor = spinnerDoctor.selectedItem as Doctor
        val specialty = spinnerSpecialities.selectedItem as Specialty

        val call = apiService.storeAppointments(authorization, description, scheduledDate, scheduledTime, type, doctor.id, specialty.id)

        call.enqueue(object: Callback<SimpleResponse>{
            override fun onResponse(
                call: Call<SimpleResponse>,
                response: Response<SimpleResponse>
            ) {
                if (response.isSuccessful) {
                    Toast.makeText(this@CreateAppointmentActivity, "La cita se realizó correctamente.", Toast.LENGTH_SHORT).show()
                    finish()
                }else {
                    Toast.makeText(this@CreateAppointmentActivity, "No se pudo hacer la cita médica.", Toast.LENGTH_SHORT).show()
                    btnConfirm.isClickable = true
                }
            }

            override fun onFailure(call: Call<SimpleResponse>, t: Throwable) {
                Toast.makeText(this@CreateAppointmentActivity, "Error: no se pudo registrar la cita médica", Toast.LENGTH_SHORT).show()
                btnConfirm.isClickable = true
            }

        })
    }

    private fun listenDoctorAndDataChanges() {
        val etScheduledDate = findViewById<EditText>(R.id.et_fecha)
        val spinnerDoctor= findViewById<Spinner>(R.id.spinner_medico)
        spinnerDoctor.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapter: AdapterView<*>?, pview: View?, position: Int, id: Long) {
                val doctor = adapter?.getItemAtPosition(position) as Doctor
                loadHours(doctor.id, etScheduledDate.text.toString())
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {

            }
        }
        // FECHAS
        etScheduledDate.addTextChangedListener(object: TextWatcher{
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                val doctor = spinnerDoctor.selectedItem as Doctor
                loadHours(doctor.id, etScheduledDate.text.toString())
            }

            override fun afterTextChanged(p0: Editable?) {

            }

        })
    }

    private fun loadHours(doctorId: Int, date: String){

            val tvSelectDoctorAndDate = findViewById<TextView>(R.id.tv_seleccionar_medico_fecha)


            if (date.isEmpty()){
                return
            }
            val call = apiService.getHours(doctorId, date)
            call.enqueue(object: Callback<Schedule> {
                override fun onResponse(call: Call<Schedule>, response: Response<Schedule>) {
                    if (response.isSuccessful){
                        val schedule = response.body()
                        schedule?.let{
                            val intervals = it.morning + it.afternoon
                            val hours = ArrayList<String>()
                            intervals.forEach{ interval ->
                                hours.add(interval.start)
                            }
                            displayRadioButtons(hours)
                        }
                    }
                }

                override fun onFailure(call: Call<Schedule>, t: Throwable) {
                    Toast.makeText(applicationContext, "Error: no se pudo cargar las horas", Toast.LENGTH_SHORT).show()
                }

            })
    }

    private fun loadSpecialties() {
        val spinnerSpecialties = findViewById<Spinner>(R.id.spinner_especialidades)
        val call = apiService.getSpecialities()
        call.enqueue(object: Callback<ArrayList<Specialty>>{
            override fun onResponse(
                call: Call<ArrayList<Specialty>>,
                response: Response<ArrayList<Specialty>>
            ) {
                if (response.isSuccessful){
                    response.body()?.let{
                        val specialties = it.toMutableList()
                        spinnerSpecialties.adapter = ArrayAdapter(this@CreateAppointmentActivity, android.R.layout.simple_list_item_1, specialties)
                    }
                }
            }

            override fun onFailure(call: Call<ArrayList<Specialty>>, t: Throwable) {
                Toast.makeText(this@CreateAppointmentActivity, "Se produjo un error al cargar las especialidades", Toast.LENGTH_SHORT).show()
            }

        })

    }

    private fun LoadDoctors(specialtyId: Int) {
        val spinnerDoctor = findViewById<Spinner>(R.id.spinner_medico)
        val call = apiService.getDoctor(specialtyId)
        call.enqueue(object: Callback<ArrayList<Doctor>>{
            override fun onResponse(
                call: Call<ArrayList<Doctor>>,
                response: Response<ArrayList<Doctor>>
            ) {
                if (response.isSuccessful){
                    response.body()?.let{
                        val doctors = it.toMutableList()
                        spinnerDoctor.adapter = ArrayAdapter(this@CreateAppointmentActivity, android.R.layout.simple_list_item_1, doctors)
                    }
                }
            }

            override fun onFailure(call: Call<ArrayList<Doctor>>, t: Throwable) {
                Toast.makeText(applicationContext, "Error, no se pudieron cargar los médicos", Toast.LENGTH_SHORT).show()
            }

        })
    }

    private fun listenSpecialtyChanges() {
        val spinnerSpecialty = findViewById<Spinner>(R.id.spinner_especialidades)
        spinnerSpecialty.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapter: AdapterView<*>?, pview: View?, position: Int, id: Long) {
                val specialty = adapter?.getItemAtPosition(position) as Specialty
                LoadDoctors(specialty.id)
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
                TODO("Not yet implemented")
            }

        }
    }

    private fun showAppointmentDataToConfirm() {
        val tvConfirmdescription = findViewById<TextView>(R.id.tv_resumen_sintomas)
        val tvConfirmSpecialty = findViewById<TextView>(R.id.tv_resumen_especialidad)
        val tvConfirmType = findViewById<TextView>(R.id.tv_resumen_tipoConsulta)
        val tvConfirmDoctorName = findViewById<TextView>(R.id.tv_resumen_medico)
        val tvConfirmDate = findViewById<TextView>(R.id.tv_resumen_fecha)
        val tvConfirmTime = findViewById<TextView>(R.id.tv_resumen_hora)

        val etDescription = findViewById<EditText>(R.id.et_description)
        val spinnerSpecialities = findViewById<Spinner>(R.id.spinner_especialidades)
        val radioGroupType = findViewById<RadioGroup>(R.id.radio_group_type)
        val spinnerDoctor = findViewById<Spinner>(R.id.spinner_medico)
        val etScheduledDate = findViewById<EditText>(R.id.et_fecha)

        tvConfirmdescription.text = etDescription.text.toString()
        tvConfirmSpecialty.text = spinnerSpecialities.selectedItem.toString()

        val selectRadioBtnId = radioGroupType.checkedRadioButtonId
        val selectedRadioType = radioGroupType.findViewById<RadioButton>(selectRadioBtnId)
        tvConfirmType.text = selectedRadioType.text.toString()

        tvConfirmDoctorName.text = spinnerDoctor.selectedItem.toString()
        tvConfirmDate.text = etScheduledDate.text.toString()
        tvConfirmTime.text = selectedRadioButton?.text.toString()
    }

    fun onClickScheduleDate(v: View?) {
        val etScheduledDate = findViewById<EditText>(R.id.et_fecha)


        val year = selectedCalendar.get(Calendar.YEAR)
        val month = selectedCalendar.get(Calendar.MONTH)
        val dayOfMonth = selectedCalendar.get(Calendar.DAY_OF_MONTH)
        val listener = DatePickerDialog.OnDateSetListener{datePicker, y, m, d ->
            selectedCalendar.set(y,m,d)
            etScheduledDate.setText(resources.getString(R.string.date_format, y, (m+1).twoDigits(), d.twoDigits()))
            etScheduledDate.error = null

        }

       val datePickerDialog = DatePickerDialog(this, listener, year, month, dayOfMonth)
       val datePicker = datePickerDialog.datePicker
       val calendar = Calendar.getInstance()
       calendar.add(Calendar.DAY_OF_MONTH, 1)
        datePicker.minDate = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 29)
        datePicker.maxDate = calendar.timeInMillis
       datePickerDialog.show()
    }

    private fun Int.twoDigits()
    = if(this > 10) this.toString() else "0$this"

    private fun displayRadioButtons(hours: ArrayList<String>){
        val radioGroupLeft = findViewById<LinearLayout>(R.id.radio_group_izq)
        val radioGroupRight = findViewById<LinearLayout>(R.id.radio_group_der)

        val tvNotAvailableHours = findViewById<TextView>(R.id.tv_horas_disponibles)

        radioGroupLeft.removeAllViews()
        radioGroupRight.removeAllViews()

        selectedRadioButton = null

        if (hours.isEmpty()){
            tvNotAvailableHours.visibility = View.VISIBLE
            return
        }
        tvNotAvailableHours.visibility = View.GONE

        var goToLeft = true


        hours.forEach {
            val radioButton = RadioButton(this)
            radioButton.id = View.generateViewId()
            radioButton.text = it

            radioButton.setOnClickListener { view ->
                selectedRadioButton?.isChecked = false
                selectedRadioButton = view as RadioButton?
                selectedRadioButton?.isChecked = true
            }

            if (goToLeft)
                radioGroupLeft.addView(radioButton)
            else
                radioGroupRight.addView(radioButton)
            goToLeft = !goToLeft
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {

        val cvNext = findViewById<CardView>(R.id.cv_siguiente)
        val cvConfirm = findViewById<CardView>(R.id.cv_confirmar)
        val cvResumen = findViewById<CardView>(R.id.cv_resumen)

        if (cvResumen.visibility == View.VISIBLE) {
            cvResumen.visibility = View.GONE
            cvConfirm.visibility = View.VISIBLE
        }else if (cvConfirm.visibility == View.VISIBLE){
            cvConfirm.visibility = View.GONE
            cvNext.visibility = View.VISIBLE
        }else if (cvNext.visibility == View.VISIBLE){
            val builder = AlertDialog.Builder(this)
            builder.setTitle("¿Estas seguro que desea salir?")
            builder.setMessage("Si abandonas el registro, los datos que había ingresado se perderán.")
            builder.setPositiveButton("Salir"){_, _->
                finish()
            }

            builder.setNegativeButton("Continuar"){dialog, _ ->
                dialog.dismiss()
            }

            val dialog = builder.create()
            dialog.show()
        }

    }
}