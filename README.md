# Code Flow Visualizer

Real-time Java code flow visualizer — edit code on the right, see the flowchart update live on the left.

## What is this?

A standalone desktop tool that turns Java source code into visual diagrams as you type. No IDE plugin required — works alongside any editor (Eclipse, IntelliJ, VS Code, etc.) via file system watching.

## Features

- **Branching Flowcharts** — `if/else` chains render as diamond decision nodes with true/false paths, `for/while` loops show loop-back arrows
- **Class Dependency Graph** — visualizes which classes reference each other
- **Architecture Overview** — shows all classes, their fields and methods at a glance
- **Live Updates** — diagrams refresh automatically as you edit code (500ms debounce)
- **File Watching** — point it at any folder and it monitors `.java` file changes from your IDE
- **Built-in Editor** — split-view with code editor, line numbers, and class navigation
- **Modular layout** — `codeflow-core` (parser + model) and `codeflow-swing-ui` (Swing widgets) can be reused from other Maven projects; the sample cart demo lives only in `codeflow-visualizer-app`

## Requirements

- Java 17+
- Maven 3.6+

## Quick Start

```bash
git clone https://github.com/mustafacebisli/CodeFlowVisualizer.git
cd CodeFlowVisualizer
mvn clean package
java -jar codeflow-visualizer-app/target/codeflow-visualizer-app-1.0-SNAPSHOT.jar
```

The shaded JAR includes FlatLaf and all modules. The app launches with a sample e-commerce cart (`CartManager`, `DiscountService`, …) bundled only in the **app** module.

## Maven modules

| Module | Role |
|--------|------|
| `codeflow-core` | Parser, domain model, `CodeFlow` prefs anchor — **no Swing** |
| `codeflow-swing-ui` | `DiagramPanel`, renderers, editor, explorer, preferences helper |
| `codeflow-visualizer-app` | `App`, `MainFrame`, bundled `examples/ecommerce-cart.java` |

Reuse in your project:

```xml
<dependency>
  <groupId>com.codeflow</groupId>
  <artifactId>codeflow-core</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>com.codeflow</groupId>
  <artifactId>codeflow-swing-ui</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

(`mvn install` on this parent first.)

## Usage

### Built-in Editor Mode

Write or paste Java code in the right panel. The left panel updates the diagram automatically.

### File Watching Mode (External IDE)

1. Click **"Klasor Izle..."** at the bottom toolbar
2. Select a folder containing `.java` files
3. Edit those files in your IDE and save
4. The diagram updates automatically

### Diagram Modes

Use the **Mod** dropdown in the left panel to switch between flowchart, dependencies, overview, and intra-class calls.

## How It Works

1. **Parsing**: `JavaSourceParser` extracts classes, fields, methods, and control flow
2. **Tree Building**: Control flow becomes a `FlowNode` tree
3. **Rendering**: `FlowchartRenderer` (and related panels) draw with `Graphics2D`
4. **Live Updates**: editor `DocumentListener` and `WatchService` with debouncing

## Building

```bash
mvn clean package
# runnable fat JAR:
# codeflow-visualizer-app/target/codeflow-visualizer-app-1.0-SNAPSHOT.jar
```

## IDE Integration

Import the **root** folder as an **Existing Maven Project**; Eclipse/IntelliJ will see the multi-module model. Run **`com.codeflow.app.App`** from the `codeflow-visualizer-app` module (classpath must include `codeflow-core` + `codeflow-swing-ui` + FlatLaf), or run the packaged JAR as above.

## License

MIT
