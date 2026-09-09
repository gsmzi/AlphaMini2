/**
 * AlphaBlock Code Generator
 * 1. JavaScript-Generator für die interaktive Ausführung (Simulator & Roboter)
 * 2. Python-Generator für den "Code ansehen"-Tab für Schüler
 */

// Hilfsfunktionen zur Ermittlung der aktiven Generatoren (Blockly v9 bis v11 kompatibel)
function getJsGenerator() {
    return (window.javascript && window.javascript.javascriptGenerator) || Blockly.JavaScript || window.javascript;
}

function getPyGenerator() {
    return (window.python && window.python.pythonGenerator) || Blockly.Python || window.python;
}

function registerGenerator(name, jsFunc, pyFunc) {
    const jsGen = getJsGenerator();
    if (jsGen) {
        if (jsGen.forBlock) jsGen.forBlock[name] = jsFunc;
        jsGen[name] = jsFunc;
    }
    if (window.Blockly && Blockly.JavaScript) {
        if (Blockly.JavaScript.forBlock) Blockly.JavaScript.forBlock[name] = jsFunc;
        Blockly.JavaScript[name] = jsFunc;
    }

    const pyGen = getPyGenerator();
    if (pyGen) {
        if (pyGen.forBlock) pyGen.forBlock[name] = pyFunc;
        pyGen[name] = pyFunc;
    }
    if (window.Blockly && Blockly.Python) {
        if (Blockly.Python.forBlock) Blockly.Python.forBlock[name] = pyFunc;
        Blockly.Python[name] = pyFunc;
    }
}

// ==========================================
// BLOCK REGISTRIERUNGEN
// ==========================================

registerGenerator('alpha_when_start',
    (block) => '// Programmstart\n',
    (block) => '# --- Programmstart ---\ndef start():\n'
);

registerGenerator('alpha_when_chest_button',
    (block) => '// Ereignis: Brustknopf\n',
    (block) => '# Ereignis: Wenn Brustknopf gedrückt\n'
);

registerGenerator('alpha_when_heard',
    (block) => `// Ereignis: Wenn gehört '${block.getFieldValue('WORD')}'\n`,
    (block) => `# Ereignis: Wenn gehört '${block.getFieldValue('WORD')}'\n`
);

registerGenerator('alpha_say',
    (block) => {
        const text = block.getFieldValue('TEXT') || '';
        const safeText = JSON.stringify(text);
        return `await runner.highlightBlock('${block.id}');\nawait runner.say(${safeText}, 'normal');\n`;
    },
    (block) => {
        const text = block.getFieldValue('TEXT') || '';
        return `    robot.say(${JSON.stringify(text)})\n`;
    }
);

registerGenerator('alpha_say_mood',
    (block) => {
        const text = block.getFieldValue('TEXT') || '';
        const mood = block.getFieldValue('MOOD') || 'happy';
        const safeText = JSON.stringify(text);
        return `await runner.highlightBlock('${block.id}');\nawait runner.say(${safeText}, '${mood}');\n`;
    },
    (block) => {
        const text = block.getFieldValue('TEXT') || '';
        const mood = block.getFieldValue('MOOD') || 'happy';
        return `    robot.say(${JSON.stringify(text)}, mood='${mood}')\n`;
    }
);

registerGenerator('alpha_sound',
    (block) => {
        const sound = block.getFieldValue('SOUND') || 'laugh';
        return `await runner.highlightBlock('${block.id}');\nawait runner.playSound('${sound}');\n`;
    },
    (block) => {
        const sound = block.getFieldValue('SOUND') || 'laugh';
        return `    robot.play_sound('${sound}')\n`;
    }
);

registerGenerator('alpha_action',
    (block) => {
        const action = block.getFieldValue('ACTION') || '010';
        return `await runner.highlightBlock('${block.id}');\nawait runner.playAction('${action}');\n`;
    },
    (block) => {
        const action = block.getFieldValue('ACTION') || '010';
        return `    robot.action('${action}')\n`;
    }
);

registerGenerator('alpha_stop_action',
    (block) => `await runner.highlightBlock('${block.id}');\nawait runner.stopAction();\n`,
    (block) => `    robot.stop_action()\n`
);

registerGenerator('alpha_head_turn',
    (block) => {
        const direction = block.getFieldValue('DIRECTION') || 'center';
        return `await runner.highlightBlock('${block.id}');\nawait runner.turnHead('${direction}');\n`;
    },
    (block) => {
        const direction = block.getFieldValue('DIRECTION') || 'center';
        return `    robot.turn_head('${direction}')\n`;
    }
);

registerGenerator('alpha_expression',
    (block) => {
        const expr = block.getFieldValue('EXPRESSION') || 'emo_007';
        return `await runner.highlightBlock('${block.id}');\nawait runner.setExpression('${expr}');\n`;
    },
    (block) => {
        const expr = block.getFieldValue('EXPRESSION') || 'emo_007';
        return `    robot.set_face('${expr}')\n`;
    }
);

registerGenerator('alpha_set_light',
    (block) => {
        const color = block.getFieldValue('COLOR') || 'green';
        return `await runner.highlightBlock('${block.id}');\nawait runner.setLight('${color}');\n`;
    },
    (block) => {
        const color = block.getFieldValue('COLOR') || 'green';
        return `    robot.set_light('${color}')\n`;
    }
);

registerGenerator('alpha_light_effect',
    (block) => {
        const effect = block.getFieldValue('EFFECT') || 'breath';
        const color = block.getFieldValue('COLOR') || 'green';
        const seconds = block.getFieldValue('SECONDS') || 3;
        return `await runner.highlightBlock('${block.id}');\nawait runner.lightEffect('${effect}', '${color}', ${seconds});\n`;
    },
    (block) => {
        const effect = block.getFieldValue('EFFECT') || 'breath';
        const color = block.getFieldValue('COLOR') || 'green';
        const seconds = block.getFieldValue('SECONDS') || 3;
        return `    robot.light_effect('${effect}', color='${color}', duration=${seconds})\n`;
    }
);

registerGenerator('alpha_wait',
    (block) => {
        const seconds = block.getFieldValue('SECONDS') || 1;
        return `await runner.highlightBlock('${block.id}');\nawait runner.wait(${seconds});\n`;
    },
    (block) => {
        const seconds = block.getFieldValue('SECONDS') || 1;
        return `    time.sleep(${seconds})\n`;
    }
);

registerGenerator('alpha_repeat_times',
    (block, gen) => {
        const times = block.getFieldValue('TIMES') || 3;
        const generator = gen || getJsGenerator();
        const branch = generator ? generator.statementToCode(block, 'DO') : '';
        return `await runner.highlightBlock('${block.id}');\nfor (let i = 0; i < ${times}; i++) {\n${branch}  if (runner.shouldStop) break;\n}\n`;
    },
    (block, gen) => {
        const times = block.getFieldValue('TIMES') || 3;
        const generator = gen || getPyGenerator();
        const branch = (generator ? generator.statementToCode(block, 'DO') : '') || '    pass\n';
        return `    for _ in range(${times}):\n${branch}`;
    }
);

registerGenerator('alpha_forever',
    (block, gen) => {
        const generator = gen || getJsGenerator();
        const branch = generator ? generator.statementToCode(block, 'DO') : '';
        return `await runner.highlightBlock('${block.id}');\nwhile (!runner.shouldStop) {\n${branch}  await runner.wait(0.05);\n}\n`;
    },
    (block, gen) => {
        const generator = gen || getPyGenerator();
        const branch = (generator ? generator.statementToCode(block, 'DO') : '') || '    pass\n';
        return `    while True:\n${branch}`;
    }
);
