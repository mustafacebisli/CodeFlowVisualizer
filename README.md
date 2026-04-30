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
- **Built-in Editor** — split-view with syntax-highlighted code editor, line numbers, and class navigation
- **Zero Dependencies** — pure Java Swing, no external libraries

## Screenshots

| Flowchart (if/else branching) | Class Dependencies |
|---|---|
| Diamond nodes for conditions, loop-back arrows for loops | Arrows showing class-to-class references |

## Requirements

- Java 17+
- Maven 3.6+

## Quick Start

```bash
git clone https://github.com/mustafacebisli/CodeFlowVisualizer.git
cd CodeFlowVisualizer
mvn compile exec:java -Dexec.mainClass="com.codeflow.App"
```

The app launches with a sample e-commerce cart codebase (`CartManager`, `DiscountService`, `StockService`, `OrderProcessor`) preloaded in the editor.

## Usage

### Built-in Editor Mode

Write or paste Java code in the right panel. The left panel updates the diagram automatically.

### File Watching Mode (External IDE)

1. Click **"Klasor Izle..."** at the bottom toolbar
2. Select a folder containing `.java` files
3. Edit those files in your IDE (Eclipse, IntelliJ, etc.)
4. The diagram updates automatically when you save

### Diagram Modes

Use the **Mod** dropdown in the left panel to switch between:

| Mode | Description |
|---|---|
| **Akis Diyagrami** | Method-level flowchart with branching logic |
| **Sinif Bagimliliklari** | Class dependency graph |
| **Genel Bakis** | Overview of all classes, fields, and methods |

### Class & Method Navigation

- **Left panel**: Select class and method from dropdowns to view its flowchart
- **Right panel**: Select a class from the dropdown to jump to its code

## Project Structure

```
src/main/java/com/codeflow/
├── App.java                    # Entry point
├── model/
│   ├── CodeClass.java          # Class data model
│   ├── CodeMethod.java         # Method data model
│   ├── FlowNode.java           # Flowchart node (supports branching tree)
│   ├── MethodCall.java         # Method call reference
│   └── DependencyGraph.java    # Class dependency graph builder
├── parser/
│   ├── JavaSourceParser.java   # Regex-based Java code parser
│   └── FileWatcher.java        # File system change monitor
├── sample/
│   └── SampleCode.java         # Sample e-commerce code
└── ui/
    ├── MainFrame.java          # Main window with split pane
    ├── CodeEditorPanel.java    # Right panel: code editor
    ├── DiagramPanel.java       # Left panel: diagram host
    ├── FlowchartRenderer.java  # Branching flowchart drawing
    ├── DependencyRenderer.java # Class dependency drawing
    └── OverviewRenderer.java   # Architecture overview drawing
```

## How It Works

1. **Parsing**: `JavaSourceParser` uses regex to extract classes, fields, methods, and control flow structures from Java source code
2. **Tree Building**: Control flow is parsed into a hierarchical `FlowNode` tree — `if/else` creates true/false branches, loops create body branches with loop-back edges
3. **Rendering**: `FlowchartRenderer` recursively draws the node tree using `Graphics2D`, calculating branch widths and merge points
4. **Live Updates**: `DocumentListener` (editor) and `WatchService` (file system) detect changes with debouncing to avoid excessive re-renders

## Building

```bash
# Compile
mvn compile

# Package as JAR
mvn package

# Run the JAR
java -jar target/code-flow-visualizer-1.0-SNAPSHOT.jar
```

## IDE Integration

### Eclipse
1. File > Import > Existing Maven Projects
2. Select the `CodeFlowVisualizer` folder
3. Run `App.java` as Java Application

### IntelliJ IDEA
1. File > Open > Select the `CodeFlowVisualizer` folder
2. IntelliJ auto-detects Maven
3. Run `App.java`

## License

MIT
