package com.example.arduinoide

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Ide {
    val chrome = Color(0xFFE6E8EB); val panel = Color(0xFFF3F4F6); val border = Color(0xFFD5DADE)
    val text = Color(0xFF1F2328); val muted = Color(0xFF5B6670); val faint = Color(0xFF6B7580)
    val teal = Color(0xFF00747A); val tealText = Color(0xFF00666C)
}

@Composable
fun IdeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Ide.teal, onPrimary = Color.White), content = content)
}

private val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp)

@Composable
private fun RoundButton(icon: ImageVector, desc: String, enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Ide.teal, contentColor = Color.White)) {
        Icon(icon, contentDescription = desc)
    }
}

@Composable
fun IdeScreen(vm: IdeViewModel) {
    val openHex = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::uploadHexFile) }
    Column(Modifier.fillMaxSize().background(Ide.panel)) {
        Row(
            Modifier.fillMaxWidth().background(Ide.chrome).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RoundButton(Icons.Default.Check, "Verificar", !vm.busy) { vm.verify() }
            RoundButton(Icons.AutoMirrored.Filled.ArrowForward, "Carregar na placa", !vm.busy) { vm.upload() }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { openHex.launch(arrayOf("*/*")) }) { Text("Abrir .hex") }
            TextButton(onClick = { vm.showLibraries = true }) { Text("Bibliotecas") }
        }
        Box(Modifier.fillMaxWidth().background(Ide.chrome)) {
            Text("Blink.ino", Modifier.background(Color.White).padding(horizontal = 16.dp, vertical = 10.dp), color = Ide.text)
        }
        val numbers = remember(vm.code) { (1..vm.code.count { it == '\n' } + 1).joinToString("\n") }
        Row(Modifier.weight(1f).fillMaxWidth().background(Color.White).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
            Text(numbers, Modifier.width(44.dp).padding(end = 10.dp), style = mono.copy(color = Ide.faint, textAlign = TextAlign.End))
            BasicTextField(
                value = vm.code, onValueChange = { vm.code = it },
                textStyle = mono.copy(color = Ide.text),
                cursorBrush = SolidColor(Ide.teal), visualTransformation = remember { CppHighlighter() },
                modifier = Modifier.horizontalScroll(rememberScrollState()).defaultMinSize(minWidth = 300.dp, minHeight = 300.dp)
            )
        }
        Column(Modifier.fillMaxWidth().height(170.dp).border(1.dp, Ide.border).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text("Saída", color = Ide.tealText, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text(vm.log, Modifier.verticalScroll(rememberScrollState()).padding(top = 6.dp),
                style = mono.copy(fontSize = 12.sp, lineHeight = 18.sp, color = Ide.text))
        }
        Box(Modifier.fillMaxWidth().background(Ide.teal).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text("Arduino Uno · CH340 · " + if (vm.busy) "Ocupado" else "Pronto", color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
fun LibraryScreen(vm: IdeViewModel) {
    BackHandler { vm.showLibraries = false }
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::installZip) }
    Column(Modifier.fillMaxSize().background(Ide.panel)) {
        Row(Modifier.fillMaxWidth().background(Ide.chrome).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.showLibraries = false }) { Text("← Voltar") }
            Text("Gerenciador de Bibliotecas", fontSize = 16.sp, color = Ide.text)
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = vm.query, onValueChange = { vm.query = it; vm.search() },
                label = { Text("Pesquisar biblioteca") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }) {
                    Text("Adicionar .ZIP (offline)")
                }
                OutlinedButton(onClick = { vm.refreshIndex() }) { Text("Atualizar índice") }
            }
            if (vm.libMessage.isNotEmpty()) Text(vm.libMessage, color = Ide.muted, fontSize = 13.sp)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.results) { e ->
                val isInstalled = vm.installed.any { it.name.equals(e.name, true) }
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(e.name, fontWeight = FontWeight.SemiBold, color = Ide.text)
                        Text("por ${e.author} · ${e.version}", fontSize = 12.sp, color = Ide.muted)
                        Text(e.sentence, fontSize = 13.sp, color = Ide.text, modifier = Modifier.padding(vertical = 6.dp))
                        Button(onClick = { vm.install(e) }) { Text(if (isInstalled) "Reinstalar" else "Instalar") }
                    }
                }
            }
            item { Text("INSTALADAS", fontSize = 12.sp, color = Ide.muted, modifier = Modifier.padding(top = 8.dp)) }
            items(vm.installed) { l ->
                Text("${l.name} ${l.version}".trim(), color = Ide.text, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}
