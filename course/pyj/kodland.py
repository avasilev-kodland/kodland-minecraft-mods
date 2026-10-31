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

VERSION = "0.1.2"

_Random = JavaClass("java.util.Random")
_System = JavaClass("java.lang.System")
_Math = JavaClass("java.lang.Math")
_Minecraft = JavaClass("net.minecraft.client.Minecraft")
_Identifier = JavaClass("net.minecraft.resources.Identifier")
_GameRules = JavaClass("net.minecraft.world.level.gamerules.GameRules")

_rng = _Random()
# What this program already did to the score line and the bar. The game answers
# a command that changes nothing with a red "Nothing changed" line, so a value
# that is already there is not sent again. "state": "unknown" (maybe left by an
# earlier program), "made" or "removed" by this program. The game runs commands a
# moment later, so after this program removed one, asking the game would still
# find it - the state is trusted instead.
_score = {"state": "unknown", "label": None}
_bar = {"state": "unknown", "label": None, "max": None, "value": None}
# Timers and events stop() was asked to stop, see stop().
_stopped = {}

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


def _server():
    # The game's own server in a single player world; None on a multiplayer server.
    return _Minecraft.getInstance().getSingleplayerServer()


def _bar_exists():
    # Asked quietly: "bossbar add" on a bar left by an earlier program is a red error.
    if _bar["state"] != "unknown":
        return _bar["state"] == "made"
    server = _server()
    if server is None:
        return False
    return server.getCustomBossEvents().get(_Identifier.parse("kod:bar")) is not None


def _quiet_chat():
    # The game answers every command with a white line ("Played sound ...",
    # "Changed the block ..."), and a program sends dozens of commands a second,
    # so the chat would be nothing but those lines. The world's rule
    # send_command_feedback turns them off. Errors (red) still show, and so does
    # everything a program writes itself with echo() or print().
    # Set on the game's own server, not with /gamerule: no line in the chat for
    # this either, and it works in a world without cheats.
    server = _server()
    if server is None:
        return
    rule = _GameRules.SEND_COMMAND_FEEDBACK
    if not server.getGameRules().get(rule):
        return

    def turn_off():
        server.getGameRules().set(rule, False, server)
    server.execute(turn_off)


def _score_exists():
    if _score["state"] != "unknown":
        return _score["state"] == "made"
    server = _server()
    if server is None:
        return False
    return server.getScoreboard().getObjective("kod_score") is not None


def here():
    """Cell where the player's feet are, as [x, y, z] whole numbers. The block under the feet is y - 1."""
    p = player_position()
    return [int(_Math.floor(p[0])), int(_Math.floor(p[1])), int(_Math.floor(p[2]))]


def block_at(dx, dy, dz):
    """Block near the player, counted like ~ in commands: block_at(0, -1, 0) is the block under the feet.
    Returns a short name: "air", "lantern", "stone"..."""
    p = here()
    block = getblock(p[0] + int(dx), p[1] + int(dy), p[2] + int(dz))
    if block is None:
        return "unknown"
    name = _name(block)
    # The game answers void_air for a chunk that is not loaded and for a cell
    # above or below the world: nothing is known there. cave_air is real air.
    if name == "void_air":
        return "unknown"
    if name == "cave_air":
        return "air"
    return name


def version():
    echo("kodland " + VERSION)
    return VERSION


# Every program that imports the library gets a quiet chat.
_quiet_chat()


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
    me = {"id": -1}

    def handler(event):
        if me["id"] in _stopped:
            return
        if event.button != 1 or event.action != 1 or event.screen is not None:
            return
        if not holding(wand):
            return
        target = player_get_targeted_block(distance)
        if target is None:
            return
        p = target.position
        action(p[0], p[1], p[2], _name(target.type))
    me["id"] = add_event_listener("mouse", handler)
    return me["id"]


def on_word(word, action):
    """The player writes this word in the chat -> action(). The word is not sent to the chat."""
    me = {"id": -1}

    def handler(event):
        if me["id"] in _stopped:
            return
        action()
    me["id"] = add_event_listener("outgoing_chat_intercept", handler,
                                  pattern="(?i)^\\s*" + str(word) + "\\s*$")
    return me["id"]


def every(ms, action):
    """Call action() every ms milliseconds. Returns an id for stop()."""
    me = {"id": -1}

    def tick():
        if me["id"] in _stopped:
            return
        action()
    me["id"] = set_interval(tick, ms)
    return me["id"]


def later(ms, action):
    """Call action() once after ms milliseconds."""
    me = {"id": -1}

    def once():
        if me["id"] in _stopped:
            return
        action()
    me["id"] = set_timeout(once, ms)
    return me["id"]


def stop(timer_id):
    """Stop a timer or an event from every(), later(), steps(), on_click(), on_word().
    Can be called from inside that timer: the rest of the function still runs."""
    # Minescript ends the program the moment its last timer or event is removed,
    # and every game call after that fails. So the timer is only marked here and
    # removed on the next frame, when the function that called stop() is done.
    _stopped[timer_id] = True

    def remove_now():
        remove_event_listener(timer_id)
    set_timeout(remove_now, 0)
    return True


def steps(items, ms, action, loop=False):
    """Call action(item) for each item, one every ms milliseconds.
    For melodies, animation frames, camera routes. loop=True repeats forever."""
    state = {"i": 0, "id": -1}

    def tick():
        if state["id"] in _stopped:
            return
        if state["i"] >= len(items):
            if loop:
                state["i"] = 0
            else:
                stop(state["id"])
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
    if _score["state"] != "made":
        # A fresh line every program: one left by an earlier program is removed first.
        if _score_exists():
            execute("scoreboard objectives remove kod_score")
        execute("scoreboard objectives add kod_score dummy " + _text(label))
        execute("scoreboard objectives setdisplay sidebar kod_score")
        _score["state"] = "made"
        _score["label"] = label
    if label != _score["label"]:
        execute("scoreboard objectives modify kod_score displayname " + _text(label))
        _score["label"] = label
    execute("scoreboard players set @a kod_score " + _n(int(value)))


def hide_score():
    """Remove the score from the screen."""
    if _score_exists():
        execute("scoreboard objectives remove kod_score")
    _score["state"] = "removed"


def bar(value, maximum, label=""):
    """Bar at the top of the screen, e.g. time left."""
    top = max(1, int(maximum))
    now = min(top, max(0, int(value)))
    if _bar["state"] != "made":
        # A fresh bar every program: one left by an earlier program is removed first.
        # A new bar has max 100, value 0, is visible and is shown to nobody.
        if _bar_exists():
            execute("bossbar remove kod:bar")
        execute("bossbar add kod:bar " + _text(label))
        execute("bossbar set kod:bar players @a")
        _bar["state"] = "made"
        _bar["label"] = label
        _bar["max"] = 100
        _bar["value"] = 0
    if label != _bar["label"]:
        execute("bossbar set kod:bar name " + _text(label))
        _bar["label"] = label
    if top != _bar["max"]:
        execute("bossbar set kod:bar max " + _n(top))
        _bar["max"] = top
    if now != _bar["value"]:
        execute("bossbar set kod:bar value " + _n(now))
        _bar["value"] = now


def hide_bar():
    """Remove the bar from the screen."""
    if _bar_exists():
        execute("bossbar remove kod:bar")
    _bar["state"] = "removed"


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
