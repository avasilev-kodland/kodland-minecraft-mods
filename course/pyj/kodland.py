# kodland.py - library of the Kodland CraftForge 10-12 course (Minescript 5.0, Pyjinn).
#
# Rule of the library: "paints and brushes, not pictures".
# One function = one simple action that is a long, hard command in the game
# (a burst of sparks, a sound, a floating block, a timer, a score line).
# Forests, mosaics, shows and games are written by the student from their own functions.
#
# Usage in a student's .pyj:
#     import kodland
#     kodland.spark(10, 70, 20)
#
# Written for Pyjinn: no Python standard library, no f-strings with escaped braces,
# no else-after-loop, no generators, no `with`.

import sys

if "Pyjinn" not in sys.version:
    raise ImportError("kodland works only in game scripts (.pyj)")

from minescript import *

VERSION = "0.1.0"

_Random = JavaClass("java.util.Random")
_System = JavaClass("java.lang.System")
_Math = JavaClass("java.lang.Math")

_rng = _Random()
_ready = {"score": False, "bar": False}

# ---------------------------------------------------------------- helpers

COLORS = {
    "red": 16711680,
    "orange": 16753920,
    "yellow": 16776960,
    "gold": 16766720,
    "lime": 8453888,
    "green": 65280,
    "aqua": 65535,
    "blue": 3355647,
    "purple": 10494192,
    "pink": 16738740,
    "white": 16777215,
}

SOUNDS = {
    "chime": "block.amethyst_block.chime",
    "magic": "block.enchantment_table.use",
    "pling": "block.note_block.pling",
    "bell": "block.bell.use",
    "boom": "entity.generic.explode",
    "levelup": "entity.player.levelup",
    "win": "ui.toast.challenge_complete",
    "door": "block.iron_door.open",
    "click": "block.stone_button.click_on",
    "whoosh": "entity.breeze.wind_burst",
    "coin": "entity.experience_orb.pickup",
    "portal": "block.portal.trigger",
}

INSTRUMENTS = ["harp", "bass", "basedrum", "snare", "hat", "bell", "flute", "chime",
               "guitar", "xylophone", "iron_xylophone", "cow_bell", "didgeridoo",
               "bit", "banjo", "pling"]


def _n(value):
    # Number as text for a command: 3 -> "3", 2.5 -> "2.5"
    return str(value)


def _f(value):
    # Float for SNBT: 0.5 -> "0.5f"
    return str(float(value)) + "f"


def _name(block_or_item):
    # "minecraft:grass_block[snowy=false]" -> "grass_block"
    text = str(block_or_item)
    cut = text.find("[")
    if cut >= 0:
        text = text[:cut]
    cut = text.find("{")
    if cut >= 0:
        text = text[:cut]
    if text.startswith("minecraft:"):
        text = text[10:]
    return text


def _id(name):
    # "gold_block" -> "minecraft:gold_block"; keeps other namespaces as they are
    text = str(name)
    if text.find(":") >= 0:
        return text
    return "minecraft:" + text


def _color(color):
    if isinstance(color, int):
        return color
    if color in COLORS:
        return COLORS[color]
    return COLORS["gold"]


def _rgb(color):
    value = _color(color)
    r = (value >> 16) % 256
    g = (value >> 8) % 256
    b = value % 256
    return "[" + _n(r / 255.0) + "," + _n(g / 255.0) + "," + _n(b / 255.0) + "]"


def _text(text):
    # Text for title / bossbar: plain SNBT string in double quotes
    return "\"" + str(text).replace("\\", "\\\\").replace("\"", "\\\"") + "\""


def here():
    """Block under the player's feet as [x, y, z] whole numbers."""
    p = player_position()
    return [int(_Math.floor(p[0])), int(_Math.floor(p[1])), int(_Math.floor(p[2]))]


def version():
    echo("kodland " + VERSION)
    return VERSION


# ---------------------------------------------------------------- module 4: light and sound

def spark(x, y, z, color="gold"):
    """Burst of sparks at a block."""
    cx = _n(x + 0.5)
    cy = _n(y + 0.5)
    cz = _n(z + 0.5)
    execute("particle minecraft:dust{color:" + _rgb(color) + ",scale:1.5} "
            + cx + " " + cy + " " + cz + " 0.4 0.4 0.4 0 40 force")
    execute("particle minecraft:end_rod " + cx + " " + cy + " " + cz + " 0.2 0.2 0.2 0.05 15 force")


def sound(name, pitch=1.0):
    """Play a short sound to the player. name: a key of SOUNDS or any game sound id."""
    sound_id = SOUNDS.get(name, name)
    execute("playsound " + _id(sound_id) + " master @a ~ ~ ~ 1 " + _n(pitch))


# ---------------------------------------------------------------- module 5: chance

def rand(a, b):
    """Random whole number from a to b (both included)."""
    low = int(min(a, b))
    high = int(max(a, b))
    return low + _rng.nextInt(high - low + 1)


def pick(items):
    """Random element of a list."""
    return items[_rng.nextInt(len(items))]


def chance(percent):
    """True with the given chance in percent: chance(20) is true 1 time in 5."""
    return _rng.nextInt(100) < percent


def seed(number):
    """Same seed -> the same random world every time."""
    global _rng
    _rng = _Random(number)


def sin(degrees):
    return _Math.sin(_Math.toRadians(float(degrees)))


def cos(degrees):
    return _Math.cos(_Math.toRadians(float(degrees)))


# ---------------------------------------------------------------- module 6: events and timers

def holding(item):
    """True if the player holds this item in the main hand."""
    hands = player_hand_items()
    if hands is None or hands.main_hand is None:
        return False
    return _name(hands.main_hand.item) == _name(item)


def on_click(action, wand="blaze_rod", distance=64):
    """Right click with the wand -> action(x, y, z, block) for the block under the crosshair."""
    def handler(event):
        if event.button != 1 or event.action != 1 or event.screen is not None:
            return
        if not holding(wand):
            return
        target = player_get_targeted_block(distance)
        if target is None:
            return
        p = target.position
        action(p[0], p[1], p[2], _name(target.type))
    return add_event_listener("mouse", handler)


def on_word(word, action):
    """The player writes this word in the chat -> action(). The word is not sent to the chat."""
    def handler(event):
        action()
    return add_event_listener("outgoing_chat_intercept", handler,
                              pattern="(?i)^\\s*" + str(word) + "\\s*$")


def every(ms, action):
    """Call action() every ms milliseconds. Returns an id for stop()."""
    return set_interval(action, ms)


def later(ms, action):
    """Call action() once after ms milliseconds."""
    return set_timeout(action, ms)


def stop(timer_id):
    """Stop a timer or an event from every(), later(), steps(), on_click(), on_word()."""
    return remove_event_listener(timer_id)


def steps(items, ms, action, loop=False):
    """Call action(item) for each item, one every ms milliseconds.
    For melodies, animation frames, camera routes. loop=True repeats forever."""
    state = {"i": 0, "id": -1}

    def tick():
        if state["i"] >= len(items):
            if loop:
                state["i"] = 0
            else:
                remove_event_listener(state["id"])
                return
        item = items[state["i"]]
        state["i"] = state["i"] + 1
        action(item)

    state["id"] = set_interval(tick, ms)
    return state["id"]


def ms():
    """Time in milliseconds - to measure how fast a program is."""
    return _System.currentTimeMillis()


# ---------------------------------------------------------------- module 6: floating blocks

def _sel(name):
    return "@e[type=minecraft:block_display,tag=kod_" + str(name) + "]"


def _transform(size):
    half = -size / 2.0
    return ("transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
            + "translation:[" + _f(half) + ",0f," + _f(half) + "],"
            + "scale:[" + _f(size) + "," + _f(size) + "," + _f(size) + "]}")


def holo(name, x, y, z, block, size=1.0):
    """Floating block (does not fall, can turn, grow and glow). name: your name for it."""
    execute("summon minecraft:block_display " + _n(x + 0.5) + " " + _n(y) + " " + _n(z + 0.5)
            + " {Tags:[\"kod\",\"kod_" + str(name) + "\"],teleport_duration:3,"
            + "block_state:{Name:\"" + _id(_name(block)) + "\"}," + _transform(size) + "}")


def turn(name, degrees):
    """Turn floating blocks with this name around the vertical axis."""
    execute("execute as " + _sel(name) + " at @s run rotate @s ~" + _n(degrees) + " ~")


def move(name, dx, dy, dz):
    """Move floating blocks with this name smoothly by dx, dy, dz."""
    execute("execute as " + _sel(name) + " at @s run tp @s ~" + _n(dx) + " ~" + _n(dy) + " ~" + _n(dz))


def grow(name, size, ticks=10):
    """Smoothly change the size of floating blocks. 20 ticks = 1 second."""
    execute("execute as " + _sel(name) + " run data merge entity @s {start_interpolation:0,interpolation_duration:"
            + _n(int(ticks)) + "," + _transform(size) + "}")


def glow(name, color="gold"):
    """Glowing outline of floating blocks."""
    execute("execute as " + _sel(name) + " run data merge entity @s {Glowing:1b,glow_color_override:"
            + _n(_color(color)) + "}")


def remove(name):
    """Remove floating blocks with this name."""
    execute("kill " + _sel(name))


def remove_all():
    """Remove all floating blocks made by kodland."""
    execute("kill @e[type=minecraft:block_display,tag=kod]")


# ---------------------------------------------------------------- module 6: music and fireworks

def note(instrument, n):
    """Note block sound. instrument: see INSTRUMENTS; n: 0..24 like clicks on a note block."""
    pitch = _Math.pow(2.0, (int(n) - 12) / 12.0)
    execute("playsound minecraft:block.note_block." + str(instrument) + " master @a ~ ~ ~ 1 " + _n(pitch))


def firework(x, y, z, color="red", shape="large_ball"):
    """Firework rocket from a block. shape: small_ball, large_ball, star, creeper, burst."""
    execute("summon minecraft:firework_rocket " + _n(x + 0.5) + " " + _n(y + 1) + " " + _n(z + 0.5)
            + " {LifeTime:30,FireworksItem:{id:\"minecraft:firework_rocket\",count:1,"
            + "components:{\"minecraft:fireworks\":{flight_duration:1,explosions:[{shape:\""
            + str(shape) + "\",colors:[I;" + _n(_color(color)) + "],has_trail:1b,has_twinkle:1b}]}}}}")


# ---------------------------------------------------------------- module 8: games

def show_score(value, label="Score"):
    """Score on the side of the screen."""
    if not _ready["score"]:
        execute("scoreboard objectives add kod_score dummy")
        _ready["score"] = True
    execute("scoreboard objectives modify kod_score displayname " + _text(label))
    execute("scoreboard objectives setdisplay sidebar kod_score")
    execute("scoreboard players set @a kod_score " + _n(int(value)))


def hide_score():
    execute("scoreboard objectives setdisplay sidebar")


def bar(value, maximum, label=""):
    """Bar at the top of the screen, e.g. time left."""
    if not _ready["bar"]:
        execute("bossbar add kod:bar " + _text(label))
        _ready["bar"] = True
    execute("bossbar set kod:bar name " + _text(label))
    execute("bossbar set kod:bar max " + _n(max(1, int(maximum))))
    execute("bossbar set kod:bar value " + _n(max(0, int(value))))
    execute("bossbar set kod:bar players @a")
    execute("bossbar set kod:bar visible true")


def hide_bar():
    execute("bossbar set kod:bar visible false")


def title(text, subtitle=""):
    """Big text in the middle of the screen."""
    if subtitle != "":
        execute("title @a subtitle " + _text(subtitle))
    execute("title @a title " + _text(text))


# ---------------------------------------------------------------- module 9: camera

def camera(x, y, z, yaw, pitch=0):
    """Put the player at x, y, z and look in the direction yaw / pitch."""
    execute("tp @s " + _n(x) + " " + _n(y) + " " + _n(z))
    player_set_orientation(yaw, pitch)
