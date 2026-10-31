package com.example.arduinoide

import java.io.File

/** Compila um sketch e devolve o texto Intel HEX. */
interface SketchCompiler {
    suspend fun compile(sketch: String, libraries: List<File>): Result<String>
}

/** TODO: empacotar avr-gcc (ARM64) como lib nativa e montar o build do core AVR. Ver README. */
class AvrGccCompiler : SketchCompiler {
    override suspend fun compile(sketch: String, libraries: List<File>): Result<String> =
        Result.failure(
            UnsupportedOperationException(
                "Compilador offline (avr-gcc) ainda não incluído. Por enquanto use \"Abrir .hex\" para testar o upload."
            )
        )
}
