# Esqueleto Maven — Apache Tika

Proyecto mínimo para la práctica de procesamiento de documentos de Recuperación de Información.

## Requisitos

- JDK 21
- Apache Maven

Comprobar la instalación:

```bash
java -version
mvn -version
```

En ambos casos Maven debe estar utilizando Java 21.

## Estructura

```text
practica-tika-maven/
├── pom.xml
├── README.md
├── documentos/
│   └── prueba.txt
└── src/
    ├── main/java/es/ugr/ri/
    │   └── Main.java
    └── test/java/es/ugr/ri/
        └── TikaTest.java
```

## Compilar

Desde el directorio que contiene `pom.xml`:

```bash
mvn clean compile
```

La primera ejecución puede tardar algo más porque Maven descargará Tika y sus dependencias.

## Comprobar el entorno mediante los tests

```bash
mvn test
```

Los tests comprueban que Tika detecta `documentos/prueba.txt` como `text/plain` y que puede extraer su contenido. Si todo está correctamente configurado, Maven finalizará con `BUILD SUCCESS`.

## Ejecutar el ejemplo

```bash
mvn exec:java -Dexec.args="documentos/prueba.txt"
```

El programa muestra el tipo MIME detectado, los metadatos disponibles y el texto extraído.

También puede sustituirse `documentos/prueba.txt` por un PDF, DOCX, HTML u otro formato soportado por los parsers estándar de Tika.

## Uso desde un IDE

Abra o importe la carpeta como **proyecto Maven**. IntelliJ IDEA, Eclipse y VS Code pueden leer `pom.xml` y descargar automáticamente las dependencias. Configure el proyecto para utilizar **JDK 21** y ejecute la clase `es.ugr.ri.Main`, pasando como argumento la ruta del documento, por ejemplo:

```text
documentos/prueba.txt
```

## Para una práctica posterior con Lucene

No será necesario reorganizar el proyecto. Bastará con añadir las dependencias de Lucene al bloque `<dependencies>` de `pom.xml` y crear las clases de indexación y búsqueda correspondientes.

## Nota sobre el logging

El proyecto incluye implementaciones sencillas para las APIs de logging utilizadas por las dependencias de Tika. Su finalidad en esta práctica es evitar avisos de configuración que podrían confundirse con errores de compilación o ejecución. El logging no forma parte de los objetivos de la práctica.
