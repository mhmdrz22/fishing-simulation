package com.example.simulation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

// Data classes for the agent-based spatial simulation
data class FishAgent(
    val id: Int,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var angle: Float,
    val maxSpeed: Float = 5.5f
)

data class SharkAgent(
    val id: Int,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var angle: Float,
    var energy: Float,
    val maxSpeed: Float = 4.8f
)

// Data class for historical tracking (used for charts)
data class SimHistoryPoint(
    val t: Float,
    val prey: Float,
    val predator: Float
)

// Main ViewModel that drives both ODE and Agent-Based Simulation
class SimulationViewModel : ViewModel() {

    // --- Core Parameters ---
    private val _r = MutableStateFlow(0.70f) // Prey birth rate
    val r = _r.asStateFlow()

    private val _a = MutableStateFlow(0.04f) // Predator hunting mortality rate
    val a = _a.asStateFlow()

    private val _s = MutableStateFlow(0.55f) // Predator death rate
    val s = _s.asStateFlow()

    private val _b = MutableStateFlow(0.015f) // Predator birth coefficient from prey
    val b = _b.asStateFlow()

    // --- Initial populations (ODE seed & initial spatial counts) ---
    private val _initPrey = MutableStateFlow(50f)
    val initPrey = _initPrey.asStateFlow()

    private val _initPredator = MutableStateFlow(12f)
    val initPredator = _initPredator.asStateFlow()

    // --- Simulation Settings ---
    private val _isPlaying = MutableStateFlow(true)
    val isPlaying = _isPlaying.asStateFlow()

    private val _simSpeed = MutableStateFlow(1) // 1x, 2x, 4x multipliers
    val simSpeed = _simSpeed.asStateFlow()

    // "Analytical" (ODE math solvers) or "Ecosystem" (Spatial 2D game)
    private val _viewMode = MutableStateFlow("Ecosystem")
    val viewMode = _viewMode.asStateFlow()

    // --- Data Streams ---
    // ODE history
    private val _odeHistory = MutableStateFlow<List<SimHistoryPoint>>(emptyList())
    val odeHistory = _odeHistory.asStateFlow()

    // Echo spatial count history (to plot graphs for our 2D spatial engine!)
    private val _ecoHistory = MutableStateFlow<List<SimHistoryPoint>>(emptyList())
    val ecoHistory = _ecoHistory.asStateFlow()

    // Live agent states
    private val _fishList = MutableStateFlow<List<FishAgent>>(emptyList())
    val fishList = _fishList.asStateFlow()

    private val _sharkList = MutableStateFlow<List<SharkAgent>>(emptyList())
    val sharkList = _sharkList.asStateFlow()

    // --- Counters & Statistics ---
    private val _fishBirthCount = MutableStateFlow(0)
    val fishBirthCount = _fishBirthCount.asStateFlow()

    private val _fishDeathCount = MutableStateFlow(0)
    val fishDeathCount = _fishDeathCount.asStateFlow()

    private val _sharkBirthCount = MutableStateFlow(0)
    val sharkBirthCount = _sharkBirthCount.asStateFlow()

    private val _sharkDeathCount = MutableStateFlow(0)
    val sharkDeathCount = _sharkDeathCount.asStateFlow()

    private val _peakPrey = MutableStateFlow(0f)
    val peakPrey = _peakPrey.asStateFlow()

    private val _peakPredator = MutableStateFlow(0f)
    val peakPredator = _peakPredator.asStateFlow()

    // Simulation Clock
    private var simTime = 0f
    private var agentIdCounter = 0
    private var simulationJob: Job? = null

    // Space width & height in normalized units
    val simWidth = 1000f
    val simHeight = 1000f

    // Standard timestep for ODE integration
    private val baseDt = 0.04f

    init {
        resetSimulation()
        startSimulationLoop()
    }

    fun setR(value: Float) { _r.value = value }
    fun setA(value: Float) { _a.value = value.coerceAtLeast(0.001f) }
    fun setS(value: Float) { _s.value = value }
    fun setB(value: Float) { _b.value = value.coerceAtLeast(0.001f) }
    
    fun setInitPrey(value: Float) { 
        _initPrey.value = value.roundToInt().toFloat() 
    }
    fun setInitPredator(value: Float) { 
        _initPredator.value = value.roundToInt().toFloat() 
    }

    fun togglePlay() { 
        _isPlaying.value = !_isPlaying.value 
    }

    fun setSpeed(speed: Int) { 
        _simSpeed.value = speed 
    }

    fun setViewMode(mode: String) { 
        _viewMode.value = mode 
    }

    // Reset everything returning both simulations back to their seed values
    fun resetSimulation() {
        simTime = 0f
        agentIdCounter = 0

        // Reset statistics
        _fishBirthCount.value = 0
        _fishDeathCount.value = 0
        _sharkBirthCount.value = 0
        _sharkDeathCount.value = 0
        _peakPrey.value = _initPrey.value
        _peakPredator.value = _initPredator.value

        // 1. Reset Analytical (ODE) Solver
        val initialPoint = SimHistoryPoint(0f, _initPrey.value, _initPredator.value)
        _odeHistory.value = listOf(initialPoint)

        // 2. Reset Spatial (Ecosystem) Agents
        val initialFish = mutableListOf<FishAgent>()
        val pCount = _initPrey.value.toInt()
        for (i in 0 until pCount) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            // Distribute cleanly around the spatial field
            initialFish.add(
                FishAgent(
                    id = ++agentIdCounter,
                    x = Random.nextFloat() * (simWidth - 100f) + 50f,
                    y = Random.nextFloat() * (simHeight - 100f) + 50f,
                    vx = cos(angle) * 3f,
                    vy = sin(angle) * 3f,
                    angle = angle
                )
            )
        }
        _fishList.value = initialFish

        val initialSharks = mutableListOf<SharkAgent>()
        val sCount = _initPredator.value.toInt()
        for (i in 0 until sCount) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            initialSharks.add(
                SharkAgent(
                    id = ++agentIdCounter,
                    x = Random.nextFloat() * (simWidth - 100f) + 50f,
                    y = Random.nextFloat() * (simHeight - 100f) + 50f,
                    vx = cos(angle) * 2.5f,
                    vy = sin(angle) * 2.5f,
                    angle = angle,
                    energy = Random.nextFloat() * 40f + 40f // Random initial energy
                )
            )
        }
        _sharkList.value = initialSharks

        // Clear spatial population graph history
        _ecoHistory.value = listOf(SimHistoryPoint(0f, pCount.toFloat(), sCount.toFloat()))
    }

    // Inject manual entities inside spatial playground (engaging sandbox!)
    fun addManualFish() {
        val angle = Random.nextFloat() * 2f * PI.toFloat()
        val newFish = FishAgent(
            id = ++agentIdCounter,
            x = Random.nextFloat() * (simWidth - 200f) + 100f,
            y = Random.nextFloat() * (simHeight - 200f) + 100f,
            vx = cos(angle) * 3.5f,
            vy = sin(angle) * 3.5f,
            angle = angle
        )
        _fishList.value = _fishList.value + newFish
        _fishBirthCount.value++
    }

    fun addManualShark() {
        val angle = Random.nextFloat() * 2f * PI.toFloat()
        val newShark = SharkAgent(
            id = ++agentIdCounter,
            x = Random.nextFloat() * (simWidth - 200f) + 100f,
            y = Random.nextFloat() * (simHeight - 200f) + 100f,
            vx = cos(angle) * 2.8f,
            vy = sin(angle) * 2.8f,
            angle = angle,
            energy = 85f // fully spawned energy
        )
        _sharkList.value = _sharkList.value + newShark
        _sharkBirthCount.value++
    }

    // High performance gameloop utilizing Coroutines
    private fun startSimulationLoop() {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            while (true) {
                if (_isPlaying.value) {
                    val multiplier = _simSpeed.value
                    
                    if (_viewMode.value == "Analytical") {
                        // Analytical integration steps
                        runAnalyticalSteps(multiplier)
                    } else {
                        // Spatial Ecosystem graphics steps
                        runSpatialSteps(multiplier)
                    }
                }
                delay(25) // approx 40fps ticks, which is extremely stable & visually smooth
            }
        }
    }

    // --- MATHEMATICAL SOLVER: RK4 (4th-order Runge Kutta) ---
    // LOTKA-VOLTERRA DIFFERENTIAL EQUES:
    // dx/dt = r*X - a*X*Y
    // dy/dt = -s*Y + b*X*Y
    private fun derivatives(x: Double, y: Double, r: Double, a: Double, s: Double, b: Double): Pair<Double, Double> {
        val dx = r * x - a * x * y
        val dy = -s * y + b * x * y
        return Pair(dx, dy)
    }

    private fun runAnalyticalSteps(multiplier: Int) {
        // Run multiple integrations per render frames depending on multiplier speed
        var currentHist = _odeHistory.value.toMutableList()
        if (currentHist.isEmpty()) return

        val rateR = _r.value.toDouble()
        val rateA = _a.value.toDouble()
        val rateS = _s.value.toDouble()
        val rateB = _b.value.toDouble()

        // Scaled dt based on multiplier
        val dt = baseDt.toDouble()

        for (step in 0 until multiplier) {
            val lastPoint = currentHist.last()
            val oldX = lastPoint.prey.toDouble()
            val oldY = lastPoint.predator.toDouble()

            // RK4 Step calculations
            val k1 = derivatives(oldX, oldY, rateR, rateA, rateS, rateB)
            val k2 = derivatives(oldX + k1.first * dt / 2.0, oldY + k1.second * dt / 2.0, rateR, rateA, rateS, rateB)
            val k3 = derivatives(oldX + k2.first * dt / 2.0, oldY + k2.second * dt / 2.0, rateR, rateA, rateS, rateB)
            val k4 = derivatives(oldX + k3.first * dt, oldY + k3.second * dt, rateR, rateA, rateS, rateB)

            val newX = oldX + (k1.first + 2.0 * k2.first + 2.0 * k3.first + k4.first) * dt / 6.0
            val newY = oldY + (k1.second + 2.0 * k2.second + 2.0 * k3.second + k4.second) * dt / 6.0

            simTime += baseDt
            val clampedX = maxOf(0.0, newX).toFloat()
            val clampedY = maxOf(0.0, newY).toFloat()

            // Record peaks
            if (clampedX > _peakPrey.value) _peakPrey.value = clampedX
            if (clampedY > _peakPredator.value) _peakPredator.value = clampedY

            currentHist.add(SimHistoryPoint(simTime, clampedX, clampedY))
        }

        // Keep a scrolling window of the last 240 simulation points for clean charts
        if (currentHist.size > 240) {
            currentHist = currentHist.subList(currentHist.size - 240, currentHist.size)
        }
        _odeHistory.value = currentHist
    }

    // --- PHYSICAL ECOSYSTEM AGENT PHYSICS & BEHAVIOR ---
    private var ecoTickCounter = 0
    private fun runSpatialSteps(multiplier: Int) {
        val currentFish = _fishList.value.map { it.copy() }
        val currentSharks = _sharkList.value.map { it.copy() }

        // Local dynamic variables based on slider equations
        val paramR = _r.value
        val paramA = _a.value
        val paramS = _s.value
        val paramB = _b.value

        var updatedFish = currentFish.toMutableList()
        var updatedSharks = currentSharks.toMutableList()

        // Loop physical steps depending on multiplier speed for natural fast-forwarding!
        for (tick in 0 until multiplier) {
            ecoTickCounter++
            val nextFish = mutableListOf<FishAgent>()
            val nextSharks = mutableListOf<SharkAgent>()

            // 1. UPDATE SPECIES: FISH (PREY) PHYSICS
            for (fish in updatedFish) {
                // Find nearby sharks to escape from (fear reflex!)
                var avoidanceVx = 0f
                var avoidanceVy = 0f
                var closestSharkDist = Float.MAX_VALUE

                // Sharks visual trigger range scales slightly with predator multiplier parameter "a"
                val fearRadius = 100f + (paramA * 500f)

                for (shark in updatedSharks) {
                    val dx = fish.x - shark.x
                    val dy = fish.y - shark.y
                    val dist = sqrt(dx*dx + dy*dy)
                    if (dist < fearRadius && dist > 1f) {
                        if (dist < closestSharkDist) {
                            closestSharkDist = dist
                        }
                        // Escaping vector added: pointing directly away from the shark
                        // Force gets stronger as danger gets closer (dist inverse)
                        val forceFactor = (fearRadius - dist) / fearRadius
                        avoidanceVx += (dx / dist) * forceFactor * 1.5f
                        avoidanceVy += (dy / dist) * forceFactor * 1.5f
                    }
                }

                // Wandering standard force: tiny organic angular offsets
                val wanderNoiseChange = 0.5f // radians
                val newAngle = fish.angle + (Random.nextFloat() * 2f - 1f) * wanderNoiseChange
                
                // Keep within bounds forces (smooth steering away from spatial fence)
                var boundaryVx = 0f
                var boundaryVy = 0f
                val margin = 80f
                if (fish.x < margin) boundaryVx += (margin - fish.x) * 0.15f
                if (fish.x > simWidth - margin) boundaryVx -= (fish.x - (simWidth - margin)) * 0.15f
                if (fish.y < margin) boundaryVy += (margin - fish.y) * 0.15f
                if (fish.y > simHeight - margin) boundaryVy -= (fish.y - (simHeight - margin)) * 0.15f

                // Assemble velocities
                var nextVx = fish.vx + cos(newAngle) * 0.45f + avoidanceVx * 1.1f + boundaryVx
                var nextVy = fish.vy + sin(newAngle) * 0.45f + avoidanceVy * 1.1f + boundaryVy

                // Speed limitations
                val speed = sqrt(nextVx * nextVx + nextVy * nextVy)
                val targetSpeed = if (closestSharkDist < fearRadius) fish.maxSpeed * 1.25f else fish.maxSpeed // flee booster!
                if (speed > targetSpeed) {
                    nextVx = (nextVx / speed) * targetSpeed
                    nextVy = (nextVy / speed) * targetSpeed
                }

                // Update position
                var nextX = fish.x + nextVx
                var nextY = fish.y + nextVy

                // Hard wall bounding check
                if (nextX < 5f || nextX > simWidth - 5f) {
                    nextVx = -nextVx
                    nextX = nextX.coerceIn(5f, simWidth - 5f)
                }
                if (nextY < 5f || nextY > simHeight - 5f) {
                    nextVy = -nextVy
                    nextY = nextY.coerceIn(5f, simHeight - 5f)
                }

                val updatedAngle = atan2(nextVy, nextVx)
                val updatedFishObj = FishAgent(
                    id = fish.id,
                    x = nextX,
                    y = nextY,
                    vx = nextVx,
                    vy = nextVy,
                    angle = updatedAngle
                )
                nextFish.add(updatedFishObj)

                // Fish reproduction rate derived from r
                // Standard frame rate chance matching Lotka-volterra expectation
                val baseBirthProb = 0.003f
                val birthProbability = baseBirthProb * paramR
                if (Random.nextFloat() < birthProbability && nextFish.size < 250) {
                    val childAngle = Random.nextFloat() * 2f * PI.toFloat()
                    nextFish.add(
                        FishAgent(
                            id = ++agentIdCounter,
                            x = nextX + (Random.nextFloat() * 15f - 7.5f),
                            y = nextY + (Random.nextFloat() * 15f - 7.5f),
                            vx = cos(childAngle) * 3f,
                            vy = sin(childAngle) * 3f,
                            angle = childAngle
                        )
                    )
                    _fishBirthCount.value++
                }
            }

            // 2. UPDATE SPECIES: SHARKS (PREDATOR) PHYSICS & FEEDING
            val currentLivingFish = nextFish.toMutableList()
            val eatenFishIds = mutableSetOf<Int>()

            for (shark in updatedSharks) {
                // Decay energy representing natural decay Rate s
                // Higher s accelerates shark energy drain
                val baseDecay = 0.12f
                val energyDecay = baseDecay * paramS
                val nextEnergy = shark.energy - energyDecay

                if (nextEnergy <= 0f) {
                    // Died of starvation
                    _sharkDeathCount.value++
                    continue
                }

                // Find closest fish to pursue (hunting behavior)
                var targetFish: FishAgent? = null
                var closestDist = Float.MAX_VALUE
                val huntRange = 240f // Sharks can smell fish from afar

                for (opp in currentLivingFish) {
                    if (opp.id in eatenFishIds) continue
                    val dx = opp.x - shark.x
                    val dy = opp.y - shark.y
                    val d = sqrt(dx*dx + dy*dy)
                    if (d < huntRange && d < closestDist) {
                        closestDist = d
                        targetFish = opp
                    }
                }

                // Steer forces
                var huntVx = 0f
                var huntVy = 0f
                var isHunting = false

                if (targetFish != null) {
                    val dx = targetFish.x - shark.x
                    val dy = targetFish.y - shark.y
                    // Steer toward the fish
                    huntVx = (dx / closestDist) * 1.5f
                    huntVy = (dy / closestDist) * 1.5f
                    isHunting = true

                    // Check if shark is close enough to visual bounds to eat the prey
                    val eatingRadius = 18f
                    if (closestDist < eatingRadius) {
                        eatenFishIds.add(targetFish.id)
                        _fishDeathCount.value++
                    }
                }

                // Wandering force (if not hunting)
                val newAngle = shark.angle + (Random.nextFloat() * 2f - 1f) * 0.40f

                // Avoid screen border forces
                var boundaryVx = 0f
                var boundaryVy = 0f
                val margin = 80f
                if (shark.x < margin) boundaryVx += (margin - shark.x) * 0.15f
                if (shark.x > simWidth - margin) boundaryVx -= (shark.x - (simWidth - margin)) * 0.15f
                if (shark.y < margin) boundaryVy += (margin - shark.y) * 0.15f
                if (shark.y > simHeight - margin) boundaryVy -= (shark.y - (simHeight - margin)) * 0.15f

                var nextVx = shark.vx + cos(newAngle) * 0.3f + huntVx * 1.20f + boundaryVx
                var nextVy = shark.vy + sin(newAngle) * 0.3f + huntVy * 1.20f + boundaryVy

                // Speed limitations
                val speed = sqrt(nextVx*nextVx + nextVy*nextVy)
                val targetSpeed = if (isHunting) shark.maxSpeed * 1.2f else shark.maxSpeed
                if (speed > targetSpeed) {
                    nextVx = (nextVx / speed) * targetSpeed
                    nextVy = (nextVy / speed) * targetSpeed
                }

                var nextX = shark.x + nextVx
                var nextY = shark.y + nextVy

                // Physical ocean wall bounce
                if (nextX < 10f || nextX > simWidth - 10f) {
                    nextVx = -nextVx
                    nextX = nextX.coerceIn(10f, simWidth - 10f)
                }
                if (nextY < 10f || nextY > simHeight - 10f) {
                    nextVy = -nextVy
                    nextY = nextY.coerceIn(10f, simHeight - 10f)
                }

                val updatedAngle = atan2(nextVy, nextVx)
                
                // Calculate energy additions if ate fish
                // If this shark ate a fish, gains energy, scales with parameter b
                val ateAny = currentLivingFish.any { it.id in eatenFishIds && abs(it.x - shark.x) < 20f && abs(it.y - shark.y) < 20f }
                var finalEnergy = nextEnergy
                if (ateAny) {
                    // Shark gains energy which scales based on prey value
                    val energyGain = 25f + (paramB * 500f)
                    finalEnergy = (finalEnergy + energyGain).coerceAtMost(100f)
                }

                val updatedSharkObj = SharkAgent(
                    id = shark.id,
                    x = nextX,
                    y = nextY,
                    vx = nextVx,
                    vy = nextVy,
                    angle = updatedAngle,
                    energy = finalEnergy
                )

                // Shark Reproduction (Division)
                // When energy is very high (above 65%), they divide physically
                val splitThreshold = 65f
                val splitProb = 0.005f * (1f + paramB * 10f)
                if (finalEnergy > splitThreshold && Random.nextFloat() < splitProb && nextSharks.size < 80) {
                    // Splitting: both parent and child get half of the remaining energy
                    val halfEnergy = finalEnergy * 0.5f
                    updatedSharkObj.energy = halfEnergy
                    nextSharks.add(updatedSharkObj)

                    val childAngle = Random.nextFloat() * 2f * PI.toFloat()
                    nextSharks.add(
                        SharkAgent(
                            id = ++agentIdCounter,
                            x = nextX + (Random.nextFloat() * 20f - 10f),
                            y = nextY + (Random.nextFloat() * 20f - 10f),
                            vx = cos(childAngle) * 2.5f,
                            vy = sin(childAngle) * 2.5f,
                            angle = childAngle,
                            energy = halfEnergy
                        )
                    )
                    _sharkBirthCount.value++
                } else {
                    nextSharks.add(updatedSharkObj)
                }
            }

            // Remove eaten fish in the safe list
            nextFish.removeAll { it.id in eatenFishIds }

            // 3. APPLY EXTINGUISHING / OVERSURVIVAL GUARDS (ensuring simulation is always interactive and fun!)
            if (nextFish.isEmpty()) {
                // Repopulate with 3 new fish
                for (i in 0 until 4) {
                    val childAngle = Random.nextFloat() * 2f * PI.toFloat()
                    nextFish.add(
                        FishAgent(
                            id = ++agentIdCounter,
                            x = Random.nextFloat() * (simWidth - 200f) + 100f,
                            y = Random.nextFloat() * (simHeight - 200f) + 100f,
                            vx = cos(childAngle) * 3f,
                            vy = sin(childAngle) * 3f,
                            angle = childAngle
                        )
                    )
                }
            }

            if (nextSharks.isEmpty()) {
                // Relocate with 2 new shark
                for (i in 0 until 2) {
                    val childAngle = Random.nextFloat() * 2f * PI.toFloat()
                    nextSharks.add(
                        SharkAgent(
                            id = ++agentIdCounter,
                            x = Random.nextFloat() * (simWidth - 200f) + 100f,
                            y = Random.nextFloat() * (simHeight - 200f) + 100f,
                            vx = cos(childAngle) * 2.5f,
                            vy = sin(childAngle) * 2.5f,
                            angle = childAngle,
                            energy = 80f
                        )
                    )
                }
            }

            updatedFish = nextFish
            updatedSharks = nextSharks
        }

        _fishList.value = updatedFish
        _sharkList.value = updatedSharks

        // Record live spikes
        val livingPrey = updatedFish.size.toFloat()
        val livingPredator = updatedSharks.size.toFloat()
        if (livingPrey > _peakPrey.value) _peakPrey.value = livingPrey
        if (livingPredator > _peakPredator.value) _peakPredator.value = livingPredator

        // Record Ecosystem dynamic history logs for the real-time eco count charts
        // log population count graphs every 10 frames to avoid high memory pressure
        if (ecoTickCounter % 10 == 0) {
            simTime += 0.40f * multiplier
            val p = SimHistoryPoint(simTime, livingPrey, livingPredator)
            var currentEcoHist = _ecoHistory.value.toMutableList()
            currentEcoHist.add(p)
            if (currentEcoHist.size > 200) {
                currentEcoHist = currentEcoHist.subList(currentEcoHist.size - 200, currentEcoHist.size)
            }
            _ecoHistory.value = currentEcoHist
        }
    }
}
