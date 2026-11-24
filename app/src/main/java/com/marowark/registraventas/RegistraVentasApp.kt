package com.marowark.registraventas

import android.app.Application
import com.marowark.registraventas.registro.Registro
import com.marowark.registraventas.registro.RegistroDatabase
import java.time.Clock

class RegistraVentasApp : Application() {
    val registro: Registro by lazy {
        val reloj = Clock.systemDefaultZone()
        Registro(RegistroDatabase.crear(this, reloj), reloj)
    }
}
