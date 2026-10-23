# Variante MIT App Inventor + Claude (opcional)

Importante: la API de Claude necesita una API key y se cobra por uso; no es gratis.
App Inventor tampoco trae FFmpeg: hace falta una extensión .aix de terceros, y las más
conocidas no son de Anthropic ni están verificadas aquí. Por eso la app principal (Kotlin)
hace el video sin librerías externas. Si aun así quieres la variante, el diseño sería:

Interfaz (Designer)
- TextBox `TemaBox` (hint: "Tema del video")
- Button `BtnGuion` ("Generar guion con IA")
- TextBox `GuionBox` (multilínea)
- Web `WebClaude` (Url = https://api.anthropic.com/v1/messages)
- Button `BtnVideo` ("Crear video")

Bloques
1. BtnGuion.Click → WebClaude.RequestHeaders = [["x-api-key", TU_KEY], ["anthropic-version","2023-06-01"], ["content-type","application/json"]]
2. WebClaude.PostText con JSON: {"model":"claude-sonnet-4-6","max_tokens":1000,"messages":[{"role":"user","content":"Escribe un guion infantil corto en español sobre: <Tema>"}]}
3. WebClaude.GotText → decodificar JSON → GuionBox.Text = content[0].text
4. BtnVideo.Click → pasar GuionBox.Text a la extensión de video (FFmpeg .aix) o abrir la app Yailen Video Maker pegando el guion.

Alternativa gratuita: genera el guion en claude.ai y pégalo en Yailen Video Maker.
