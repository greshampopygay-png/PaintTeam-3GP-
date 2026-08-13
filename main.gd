extends Node3D

const MAP_SCENE = preload("res://assets/models/map.gltf")
const PLAYER_SCENE = preload("res://assets/models/player.gltf")
const RED_SCENE = preload("res://assets/models/red.gltf")
const GREEN_SCENE = preload("res://assets/models/green.gltf")
const YELLOW_SCENE = preload("res://assets/models/yellow.gltf")
const GUN_SCENE = preload("res://assets/models/gun.gltf")
const CAN_BLUE = preload("res://assets/models/can_blue.gltf")
const CAN_RED = preload("res://assets/models/can_red.gltf")
const CAN_GREEN = preload("res://assets/models/can_green.gltf")
const CAN_YELLOW = preload("res://assets/models/can_yellow.gltf")

var player: CharacterBody3D
var camera: Camera3D
var yaw := 0.0
var pitch := -0.12
var move_input := Vector2.ZERO
var look_touch := -1
var move_touch := -1
var fire_touch := -1
var last_look := Vector2.ZERO
var health := 100
var ammo := 30
var enemies: Array[CharacterBody3D] = []
var health_label: Label
var ammo_label: Label
var message_label: Label

func _ready() -> void:
    _build_world()
    _build_player()
    _build_ui()
    _spawn_enemies()
    _spawn_cans()
    _update_hud()

func _build_world() -> void:
    var map = MAP_SCENE.instantiate()
    add_child(map)
    map.position = Vector3.ZERO
    var env := WorldEnvironment.new()
    var environment := Environment.new()
    environment.background_mode = Environment.BG_COLOR
    environment.background_color = Color("#111827")
    environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    environment.ambient_light_color = Color(0.75, 0.78, 0.85)
    environment.ambient_light_energy = 0.7
    environment.tonemap_mode = Environment.TONE_MAPPER_FILMIC
    env.environment = environment
    add_child(env)
    var sun := DirectionalLight3D.new()
    sun.rotation_degrees = Vector3(-55, -25, 0)
    sun.light_energy = 1.2
    sun.shadow_enabled = true
    add_child(sun)

func _build_player() -> void:
    player = CharacterBody3D.new()
    player.name = "Player"
    add_child(player)
    player.position = Vector3(0, 1.0, 5)
    var shape := CollisionShape3D.new()
    var capsule := CapsuleShape3D.new()
    capsule.height = 1.6
    capsule.radius = 0.35
    shape.shape = capsule
    shape.position.y = 0.8
    player.add_child(shape)
    var visual = PLAYER_SCENE.instantiate()
    visual.scale = Vector3.ONE * 1.1
    player.add_child(visual)
    var head := Node3D.new()
    head.name = "Head"
    head.position = Vector3(0, 1.55, 0)
    player.add_child(head)
    camera = Camera3D.new()
    camera.current = true
    camera.fov = 72
    camera.position = Vector3(0, 0.25, 3.2)
    camera.rotation_degrees = Vector3(-4, 180, 0)
    head.add_child(camera)
    var gun = GUN_SCENE.instantiate()
    gun.scale = Vector3.ONE * 0.55
    gun.position = Vector3(0.48, -0.3, -0.75)
    gun.rotation_degrees = Vector3(0, 180, 0)
    camera.add_child(gun)

func _spawn_enemies() -> void:
    var spots = [Vector3(5, 0.9, -5), Vector3(-6, 0.9, -3), Vector3(7, 0.9, 6), Vector3(-7, 0.9, 7)]
    var scenes = [RED_SCENE, GREEN_SCENE, YELLOW_SCENE, RED_SCENE]
    for i in spots.size():
        var e := CharacterBody3D.new()
        e.name = "Enemy_%d" % i
        add_child(e)
        e.position = spots[i]
        var shape := CollisionShape3D.new()
        var capsule := CapsuleShape3D.new()
        capsule.height = 1.5
        capsule.radius = 0.35
        shape.shape = capsule
        shape.position.y = 0.75
        e.add_child(shape)
        var visual = scenes[i].instantiate()
        visual.scale = Vector3.ONE * 1.1
        e.add_child(visual)
        e.set_meta("hp", 3)
        enemies.append(e)

func _spawn_cans() -> void:
    var data = [
        [CAN_BLUE, Vector3(-3, 0.45, 1)], [CAN_RED, Vector3(3, 0.45, 1)],
        [CAN_GREEN, Vector3(-2, 0.45, -7)], [CAN_YELLOW, Vector3(2, 0.45, -7)]
    ]
    for item in data:
        var can = item[0].instantiate()
        can.position = item[1]
        can.scale = Vector3.ONE * 0.8
        add_child(can)

func _build_ui() -> void:
    var layer := CanvasLayer.new()
    add_child(layer)
    var hud := Control.new()
    hud.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    layer.add_child(hud)

    health_label = Label.new()
    health_label.position = Vector2(24, 20)
    health_label.add_theme_font_size_override("font_size", 28)
    hud.add_child(health_label)

    ammo_label = Label.new()
    ammo_label.position = Vector2(24, 58)
    ammo_label.add_theme_font_size_override("font_size", 24)
    hud.add_child(ammo_label)

    message_label = Label.new()
    message_label.position = Vector2(0, 20)
    message_label.size = Vector2(1280, 60)
    message_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    message_label.add_theme_font_size_override("font_size", 26)
    hud.add_child(message_label)

    var cross := Label.new()
    cross.text = "+"
    cross.position = Vector2(640 - 12, 360 - 25)
    cross.add_theme_font_size_override("font_size", 38)
    hud.add_child(cross)

    var joy := TouchJoystick.new()
    joy.position = Vector2(35, 480)
    joy.size = Vector2(230, 230)
    joy.move_changed.connect(_on_joystick)
    hud.add_child(joy)

    var fire := Button.new()
    fire.text = "КРАСКА"
    fire.position = Vector2(1050, 500)
    fire.size = Vector2(180, 150)
    fire.pressed.connect(_fire)
    hud.add_child(fire)

    var hint := Label.new()
    hint.text = "Левая сторона — движение • Правая — обзор • Кнопка — стрелять"
    hint.position = Vector2(300, 650)
    hint.size = Vector2(680, 40)
    hint.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    hint.add_theme_font_size_override("font_size", 18)
    hud.add_child(hint)

func _on_joystick(v: Vector2) -> void:
    move_input = v

func _unhandled_input(event: InputEvent) -> void:
    if event is InputEventScreenTouch:
        if event.pressed:
            if event.position.x > 950 and event.position.y > 450:
                fire_touch = event.index
                _fire()
            elif event.position.x < 320 and event.position.y > 430:
                move_touch = event.index
            elif event.position.x > 320:
                look_touch = event.index
                last_look = event.position
        else:
            if event.index == move_touch: move_touch = -1
            if event.index == look_touch: look_touch = -1
            if event.index == fire_touch: fire_touch = -1
    elif event is InputEventScreenDrag and event.index == look_touch:
        var delta = event.position - last_look
        last_look = event.position
        yaw -= delta.x * 0.008
        pitch = clamp(pitch - delta.y * 0.006, -0.9, 0.65)
    elif event is InputEventMouseMotion and Input.is_mouse_button_pressed(MOUSE_BUTTON_RIGHT):
        yaw -= event.relative.x * 0.006
        pitch = clamp(pitch - event.relative.y * 0.006, -0.9, 0.65)
    elif event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and event.pressed:
        _fire()

func _physics_process(delta: float) -> void:
    if not player: return
    var keyboard = Input.get_vector("move_left", "move_right", "move_forward", "move_back")
    var input_vec = move_input if move_input.length() > 0.05 else keyboard
    var forward = -player.global_transform.basis.z
    var right = player.global_transform.basis.x
    var dir = (right * input_vec.x + forward * input_vec.y)
    if dir.length() > 1: dir = dir.normalized()
    player.velocity.x = dir.x * 5.0
    player.velocity.z = dir.z * 5.0
    player.velocity.y = -0.2
    player.move_and_slide()
    player.rotation.y = yaw
    camera.rotation.x = pitch
    for e in enemies:
        if not is_instance_valid(e): continue
        var to_player = player.global_position - e.global_position
        to_player.y = 0
        if to_player.length() > 1.8:
            e.velocity = to_player.normalized() * 1.4
            e.move_and_slide()
        elif Time.get_ticks_msec() % 1000 < 35:
            health = max(0, health - 5)
            _update_hud()
            if health <= 0:
                message_label.text = "ТЫ ПРОИГРАЛ — нажми КРАСКА для перезапуска"
    if Input.is_action_pressed("fire"):
        _fire()

func _fire() -> void:
    if ammo <= 0:
        message_label.text = "НЕТ КРАСКИ"
        return
    ammo -= 1
    _update_hud()
    var origin = camera.global_position
    var target = origin + (-camera.global_transform.basis.z * 30.0)
    var query := PhysicsRayQueryParameters3D.create(origin, target)
    query.collide_with_areas = true
    var hit = get_world_3d().direct_space_state.intersect_ray(query)
    if hit and hit.collider:
        var obj = hit.collider
        var root = obj
        while root and not root.has_meta("hp") and root.get_parent() != null:
            root = root.get_parent()
        if root and root.has_meta("hp"):
            var hp = int(root.get_meta("hp")) - 1
            root.set_meta("hp", hp)
            message_label.text = "ПОПАДАНИЕ!"
            if hp <= 0:
                enemies.erase(root)
                root.queue_free()
                message_label.text = "ЧЕЛИК ПОКРАШЕН!"
        else:
            message_label.text = "Мимо!"

func _update_hud() -> void:
    if health_label:
        health_label.text = "❤ %d" % health
    if ammo_label:
        ammo_label.text = "🎨 %d" % ammo

class TouchJoystick extends Control:
    signal move_changed(value: Vector2)
    var active := false
    var value := Vector2.ZERO
    var center := Vector2.ZERO
    var radius := 90.0
    func _ready() -> void:
        mouse_filter = Control.MOUSE_FILTER_STOP
        queue_redraw()
    func _gui_input(event: InputEvent) -> void:
        if event is InputEventScreenTouch:
            active = event.pressed
            if active: _set_value(event.position + global_position)
            else: _set_value(global_position + size * 0.5)
        elif event is InputEventScreenDrag:
            _set_value(event.position + global_position)
        elif event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
            active = event.pressed
            if active: _set_value(event.position)
            else: _set_value(global_position + size * 0.5)
        elif event is InputEventMouseMotion and active:
            _set_value(event.position)
    func _set_value(pos: Vector2) -> void:
        center = global_position + size * 0.5
        value = (pos - center) / radius
        if value.length() > 1: value = value.normalized()
        move_changed.emit(Vector2(value.x, -value.y))
        queue_redraw()
    func _draw() -> void:
        draw_circle(size * 0.5, radius, Color(0.05,0.05,0.05,0.38))
        draw_circle(size * 0.5 + Vector2(value.x, -value.y) * radius * 0.55, radius * 0.48, Color(1,1,1,0.30))

