/**
 * AlphaBlock - Fertige Unterrichtsbeispiele für die 5. Klasse
 */

const ALPHA_EXAMPLES = {
    // ------------------------------------------------------------------------
    // Beispiel 1: Begrüßung & Vorstellen
    // ------------------------------------------------------------------------
    'greeting': {
        name: '👋 Begrüßung & Kennenlernen',
        description: 'Alpha Mini stellt sich der Klasse vor, lächelt, schaltet blaue Lichter an und winkt.',
        xml: `<xml xmlns="https://developers.google.com/blockly/xml">
  <block type="alpha_when_start" x="40" y="40">
    <next>
      <block type="alpha_expression">
        <field name="EXPRESSION">emo_007</field>
        <next>
          <block type="alpha_set_light">
            <field name="COLOR">blue</field>
            <next>
              <block type="alpha_say">
                <field name="TEXT">Hallo zusammen! Ich bin Alpha Mini, euer Klassen-Roboter!</field>
                <next>
                  <block type="alpha_action">
                    <field name="ACTION">010</field>
                    <next>
                      <block type="alpha_expression">
                        <field name="EXPRESSION">wink</field>
                        <next>
                          <block type="alpha_say">
                            <field name="TEXT">Ich freue mich sehr darauf, mit euch das Programmieren zu lernen!</field>
                          </block>
                        </next>
                      </block>
                    </next>
                  </block>
                </next>
              </block>
            </next>
          </block>
        </next>
      </block>
    </next>
  </block>
</xml>`
    },

    // ------------------------------------------------------------------------
    // Beispiel 2: Roboter Dance-Party
    // ------------------------------------------------------------------------
    'dance': {
        name: '💃 Roboter Dance-Party',
        description: 'Blinkende Party-Lichter, Fanfare, cooler Tanz und Jubel am Ende!',
        xml: `<xml xmlns="https://developers.google.com/blockly/xml">
  <block type="alpha_when_start" x="40" y="40">
    <next>
      <block type="alpha_expression">
        <field name="EXPRESSION">emo_008</field>
        <next>
          <block type="alpha_sound">
            <field name="SOUND">fanfare</field>
            <next>
              <block type="alpha_say_mood">
                <field name="TEXT">Achtung, fertig, Dance-Party!</field>
                <field name="MOOD">excited</field>
                <next>
                  <block type="alpha_light_effect">
                    <field name="EFFECT">blink</field>
                    <field name="COLOR">purple</field>
                    <field name="SECONDS">3</field>
                    <next>
                      <block type="alpha_action">
                        <field name="ACTION">014</field>
                        <next>
                          <block type="alpha_sound">
                            <field name="SOUND">cheer</field>
                            <next>
                              <block type="alpha_say">
                                <field name="TEXT">Yeah! Wie waren meine Moves?</field>
                                <next>
                                  <block type="alpha_action">
                                    <field name="ACTION">016</field>
                                  </block>
                                </next>
                              </block>
                            </next>
                          </block>
                        </next>
                      </block>
                    </next>
                  </block>
                </next>
              </block>
            </next>
          </block>
        </next>
      </block>
    </next>
  </block>
</xml>`
    },

    // ------------------------------------------------------------------------
    // Beispiel 3: Witze-Erzähler
    // ------------------------------------------------------------------------
    'joke': {
        name: '😂 Witze-Erzähler',
        description: 'Alpha Mini erzählt einen lustigen Schülerwitz, baut Spannung auf und lacht mit.',
        xml: `<xml xmlns="https://developers.google.com/blockly/xml">
  <block type="alpha_when_start" x="40" y="40">
    <next>
      <block type="alpha_expression">
        <field name="EXPRESSION">emo_010</field>
        <next>
          <block type="alpha_say">
            <field name="TEXT">Hey Kinder! Wollt ihr einen Roboter-Witz hören?</field>
            <next>
              <block type="alpha_wait">
                <field name="SECONDS">1.5</field>
                <next>
                  <block type="alpha_say">
                    <field name="TEXT">Warum geht der Roboter in den Ferien an den Strand?</field>
                    <next>
                      <block type="alpha_wait">
                        <field name="SECONDS">2</field>
                        <next>
                          <block type="alpha_expression">
                            <field name="EXPRESSION">emo_007</field>
                            <next>
                              <block type="alpha_say">
                                <field name="TEXT">Um seine Batterien wieder aufzuladen!</field>
                                <next>
                                  <block type="alpha_sound">
                                    <field name="SOUND">laugh</field>
                                    <next>
                                      <block type="alpha_action">
                                        <field name="ACTION">018</field>
                                      </block>
                                    </next>
                                  </block>
                                </next>
                              </block>
                            </next>
                          </block>
                        </next>
                      </block>
                    </next>
                  </block>
                </next>
              </block>
            </next>
          </block>
        </next>
      </block>
    </next>
  </block>
</xml>`
    },

    // ------------------------------------------------------------------------
    // Beispiel 4: Roboter-Gymnastik
    // ------------------------------------------------------------------------
    'fitness': {
        name: '🤸 Roboter-Gymnastik & Laufen',
        description: 'Morgen-Workout für die Klasse: Vorwärts laufen, Liegestütze, Kniebeuge und Klatschen.',
        xml: `<xml xmlns="https://developers.google.com/blockly/xml">
  <block type="alpha_when_start" x="40" y="40">
    <next>
      <block type="alpha_set_light">
        <field name="COLOR">green</field>
        <next>
          <block type="alpha_say">
            <field name="TEXT">Guten Morgen! Zeit für unser Roboter-Fitnesstraining! Macht alle mit!</field>
            <next>
              <block type="alpha_say">
                <field name="TEXT">Erste Übung: 2 Schritte vorwärts laufen!</field>
                <next>
                  <block type="alpha_walk">
                    <field name="DIRECTION">forward</field>
                    <field name="STEPS">2</field>
                    <next>
                      <block type="alpha_say">
                        <field name="TEXT">Zweite Übung: Und jetzt Liegestütze machen!</field>
                        <next>
                          <block type="alpha_action">
                            <field name="ACTION">pressup</field>
                            <next>
                              <block type="alpha_say">
                                <field name="TEXT">Sehr stark! Jetzt 2 Schritte zurück und tief in die Kniebeuge!</field>
                                <next>
                                  <block type="alpha_walk">
                                    <field name="DIRECTION">backward</field>
                                    <field name="STEPS">2</field>
                                    <next>
                                      <block type="alpha_action">
                                        <field name="ACTION">031</field>
                                        <next>
                                          <block type="alpha_say">
                                            <field name="TEXT">Super gemacht! Großer Applaus für euch alle!</field>
                                            <next>
                                              <block type="alpha_sound">
                                                <field name="SOUND">applause</field>
                                                <next>
                                                  <block type="alpha_action">
                                                    <field name="ACTION">018</field>
                                                  </block>
                                                </next>
                                              </block>
                                            </next>
                                          </block>
                                        </next>
                                      </block>
                                    </next>
                                  </block>
                                </next>
                              </block>
                            </next>
                          </block>
                        </next>
                      </block>
                    </next>
                  </block>
                </next>
              </block>
            </next>
          </block>
        </next>
      </block>
    </next>
  </block>
</xml>`
    },

    // ------------------------------------------------------------------------
    // Beispiel 5: Das Ampel-Spiel
    // ------------------------------------------------------------------------
    'traffic_light': {
        name: '🚦 Das Ampel-Spiel',
        description: 'Schaltet Rot, Gelb und Grün mit LEDs: Bei Grün wird getanzt, bei Rot gestoppt!',
        xml: `<xml xmlns="https://developers.google.com/blockly/xml">
  <block type="alpha_when_start" x="40" y="40">
    <next>
      <block type="alpha_say">
        <field name="TEXT">Wir spielen das Ampel-Spiel! Achtet genau auf meine Lichter!</field>
        <next>
          <block type="alpha_set_light">
            <field name="COLOR">red</field>
            <next>
              <block type="alpha_expression">
                <field name="EXPRESSION">emo_002</field>
                <next>
                  <block type="alpha_say">
                    <field name="TEXT">Rot! Alle müssen wie eine Statue stillstehen!</field>
                    <next>
                      <block type="alpha_wait">
                        <field name="SECONDS">3</field>
                        <next>
                          <block type="alpha_set_light">
                            <field name="COLOR">yellow</field>
                            <next>
                              <block type="alpha_say">
                                <field name="TEXT">Gelb! Auf die Plätze...</field>
                                <next>
                                  <block type="alpha_wait">
                                    <field name="SECONDS">1.5</field>
                                    <next>
                                      <block type="alpha_set_light">
                                        <field name="COLOR">green</field>
                                        <next>
                                          <block type="alpha_expression">
                                            <field name="EXPRESSION">emo_007</field>
                                            <next>
                                              <block type="alpha_say">
                                                <field name="TEXT">Grün! Jetzt dürft ihr euch wieder bewegen!</field>
                                                <next>
                                                  <block type="alpha_action">
                                                    <field name="ACTION">010</field>
                                                  </block>
                                                </next>
                                              </block>
                                            </next>
                                          </block>
                                        </next>
                                      </block>
                                    </next>
                                  </block>
                                </next>
                              </block>
                            </next>
                          </block>
                        </next>
                      </block>
                    </next>
                  </block>
                </next>
              </block>
            </next>
          </block>
        </next>
      </block>
    </next>
  </block>
</xml>`
    },

    // ------------------------------------------------------------------------
    // Beispiel 6: Gefühle-Theater
    // ------------------------------------------------------------------------
    'emotions': {
        name: '🎭 Gefühle-Theater',
        description: 'Alpha Mini durchlebt verschiedene Gefühle (überrascht, traurig, verliebt, glücklich).',
        xml: `<xml xmlns="https://developers.google.com/blockly/xml">
  <block type="alpha_when_start" x="40" y="40">
    <next>
      <block type="alpha_expression">
        <field name="EXPRESSION">codemao8</field>
        <next>
          <block type="alpha_set_light">
            <field name="COLOR">yellow</field>
            <next>
              <block type="alpha_say">
                <field name="TEXT">Huch! Was war denn das? Ich bin ganz überrascht!</field>
                <next>
                  <block type="alpha_wait">
                    <field name="SECONDS">2</field>
                    <next>
                      <block type="alpha_expression">
                        <field name="EXPRESSION">emo_014</field>
                        <next>
                          <block type="alpha_set_light">
                            <field name="COLOR">blue</field>
                            <next>
                              <block type="alpha_say_mood">
                                <field name="TEXT">Jetzt bin ich traurig, weil die Pause schon vorbei ist...</field>
                                <field name="MOOD">sad</field>
                                <next>
                                  <block type="alpha_wait">
                                    <field name="SECONDS">2.5</field>
                                    <next>
                                      <block type="alpha_expression">
                                        <field name="EXPRESSION">emo_006</field>
                                        <next>
                                          <block type="alpha_set_light">
                                            <field name="COLOR">purple</field>
                                            <next>
                                              <block type="alpha_say">
                                                <field name="TEXT">Aber ich habe euch doch so lieb! Danke, dass ihr bei mir seid!</field>
                                                <next>
                                                  <block type="alpha_sound">
                                                    <field name="SOUND">cheer</field>
                                                  </block>
                                                </next>
                                              </block>
                                            </next>
                                          </block>
                                        </next>
                                      </block>
                                    </next>
                                  </block>
                                </next>
                              </block>
                            </next>
                          </block>
                        </next>
                      </block>
                    </next>
                  </block>
                </next>
              </block>
            </next>
          </block>
        </next>
      </block>
    </next>
  </block>
</xml>`
    },

    // ------------------------------------------------------------------------
    // Beispiel 7: Roboter-Haustier & Sensoren
    // ------------------------------------------------------------------------
    'pet': {
        name: '🐱 7. Roboter-Haustier & Sensoren',
        description: 'Alpha Mini schläft, wacht beim Streicheln mit Herzaugen auf, steht auf und reagiert mit bunten Lichtern!',
        xml: `<xml xmlns="https://developers.google.com/blockly/xml">
  <block type="alpha_when_start" x="40" y="40">
    <next>
      <block type="alpha_expression">
        <field name="EXPRESSION">sleepy</field>
        <next>
          <block type="alpha_light_effect">
            <field name="EFFECT">breath</field>
            <field name="COLOR">cyan</field>
            <field name="SECONDS">3</field>
            <next>
              <block type="alpha_say">
                <field name="TEXT">Gähn... Ich bin müde und mache ein kleines Nickerchen.</field>
                <next>
                  <block type="alpha_action">
                    <field name="ACTION">lie_down</field>
                    <next>
                      <block type="alpha_wait">
                        <field name="SECONDS">2</field>
                        <next>
                          <block type="alpha_say">
                            <field name="TEXT">Tippe auf Kopf berühren oder streichle mich am Kopf!</field>
                            <next>
                              <block type="alpha_expression">
                                <field name="EXPRESSION">love</field>
                                <next>
                                  <block type="alpha_sound">
                                    <field name="SOUND">giggle</field>
                                    <next>
                                      <block type="alpha_say_mood">
                                        <field name="TEXT">Mmmh, das kitzelt! Ich wache wieder auf!</field>
                                        <field name="MOOD">excited</field>
                                        <next>
                                          <block type="alpha_action">
                                            <field name="ACTION">standup</field>
                                            <next>
                                              <block type="alpha_light_effect">
                                                <field name="EFFECT">cycle</field>
                                                <field name="COLOR">rainbow</field>
                                                <field name="SECONDS">3</field>
                                                <next>
                                                  <block type="alpha_say">
                                                    <field name="TEXT">Hallo mein Freund! Danke fürs Streicheln!</field>
                                                    <next>
                                                      <block type="alpha_action">
                                                        <field name="ACTION">010</field>
                                                      </block>
                                                    </next>
                                                  </block>
                                                </next>
                                              </block>
                                            </next>
                                          </block>
                                        </next>
                                      </block>
                                    </next>
                                  </block>
                                </next>
                              </block>
                            </next>
                          </block>
                        </next>
                      </block>
                    </next>
                  </block>
                </next>
              </block>
            </next>
          </block>
        </next>
      </block>
    </next>
  </block>
</xml>`
    }
};

if (typeof window !== 'undefined') {
    window.ALPHA_EXAMPLES = ALPHA_EXAMPLES;
}

