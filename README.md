# redux

A small, TCA-style (The Composable Architecture) state-management library for
Kotlin/Android. Pure reducers, effects parameterized by the action they feed back
(`Effect<Action>`), a single-runtime `Store`, and operators to compose features —
`scope` for a single child and `forEach` for a dynamic collection.

- **Artifact:** `com.florentmaufras:redux`
- **Latest:** `3.1.1`
- **Requires:** `minSdk 24`, Kotlin, kotlinx.coroutines, AndroidX `ViewModel`

This repo also contains a demo app (`:app`) that exercises the whole surface; see
[`docs/TESTING.md`](docs/TESTING.md) for how each layer is tested.

## Install

The library is published to **GitHub Packages**, which requires a token with the
`read:packages` scope.

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/florentmaufras/redux")
            credentials {
                // A GitHub username + a PAT/token with read:packages.
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

Module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.florentmaufras:redux:3.1.1")
}
```

> Every build environment (including CI) needs the `read:packages` token to
> resolve the dependency.

## Core concepts

| Type | Role |
|------|------|
| `Reducer<State, Action>` | Pure `reduce(state, action) -> ReduceResult`. No side effects run here. |
| `ReduceResult<State, Action>(state, effect)` | The next state + an `Effect` (default `Effect.none()`). |
| `Effect<Action>` | Async work that feeds actions back. `none` / `run` / `send` / `merge` / `map` / `cancellable` / `cancel`. |
| `Store<State, Action>` | An AndroidX `ViewModel`. `send(action)` reduces, commits `state`, runs the effect, and re-sends emitted actions. |

## Quick start

```kotlin
import com.florentmaufras.redux.*

data class CounterState(val count: Int = 0, val loading: Boolean = false)

sealed interface CounterAction {
    data object Increment : CounterAction
    data object Decrement : CounterAction
    data object LoadRandom : CounterAction
    data class Loaded(val value: Int) : CounterAction
}

class CounterReducer(
    private val service: NumberService,
) : Reducer<CounterState, CounterAction> {
    override fun reduce(
        state: CounterState,
        action: CounterAction,
    ): ReduceResult<CounterState, CounterAction> = when (action) {
        CounterAction.Increment -> ReduceResult(state.copy(count = state.count + 1))
        CounterAction.Decrement -> ReduceResult(state.copy(count = state.count - 1))
        CounterAction.LoadRandom -> ReduceResult(
            state.copy(loading = true),
            // Side effects live in Effect.run { }; dependencies are injected into the reducer.
            Effect.run { emit(CounterAction.Loaded(service.random())) },
        )
        is CounterAction.Loaded -> ReduceResult(state.copy(count = action.value, loading = false))
    }
}

class CounterStore(
    override val reducer: CounterReducer = CounterReducer(NumberService()),
) : Store<CounterState, CounterAction>(CounterState())
```

In Compose, observe `state` and dispatch with `send`:

```kotlin
@Composable
fun CounterScreen(store: CounterStore) {
    val state by store.state.collectAsState()
    Column {
        Text("Count: ${state.count}")
        Button(onClick = { store.send(CounterAction.Increment) }) { Text("+") }
        Button(onClick = { store.send(CounterAction.LoadRandom) }) { Text("Random") }
    }
}
```

## Effects

An `Effect<Action>` is async work that emits actions back into the store:

```kotlin
Effect.none()                                  // do nothing
Effect.send(MyAction.Done)                     // emit one action
Effect.run { emit(MyAction.Tick) }             // suspend/Flow block; emit as many as you like
Effect.merge(a, b)                             // run several effects
Effect.run { … }.cancellable(id)               // track under `id` so it can be cancelled
Effect.cancel(id)                              // cancel the in-flight effect(s) under `id`
```

Cancel ids share one store-global namespace, so use distinct/unique keys (an
`enum`/`object`, or a composite like `id to "tick"`).

## Composition

Compose a single child feature with `scope` (a state lens + action prism):

```kotlin
fun <PS, PA, CS, CA> Reducer<PS, PA>.scope(
    scope: Scope<PS, PA, CS, CA>,
    child: Reducer<CS, CA>,
): Reducer<PS, PA>
```

Compose a dynamic `0..X` collection of identified children with `forEach`
(child state implements `Identifiable<Id>`):

```kotlin
fun <PS, PA, CS, CA, Id> Reducer<PS, PA>.forEach(
    scope: ForEachScope<PS, PA, CS, CA, Id>,
    child: Reducer<CS, CA>,
): Reducer<PS, PA> where CS : Identifiable<Id>
```

A parent runs first (so it can handle add/remove/broadcast and observe child
actions), then the child action is routed to its slice and the child's effects are
lifted back up to parent actions. The demo's chronometers feature nests both:
`App -> Chronometers (forEach over a list) + Universities`, all under one `Store`.
See `app/src/main/java/com/florentmaufras/reduxdemo/` for a full example.

## Testing

Reducers are pure — call `reduce` and assert the returned state. For effects, use
`TestStore`, which runs the real store loop against injected fakes and exposes
`currentState` and `receivedActions`:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class CounterStoreTest {

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadRandom_setsCount() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = TestStore(CounterState(), CounterReducer(FakeNumberService(value = 42)))

        store.send(CounterAction.LoadRandom)
        advanceUntilIdle()

        assertEquals(42, store.currentState.count)
        assertEquals(CounterAction.Loaded(42), store.receivedActions.last())
    }
}
```

Effects that use `delay` need a test-scheduler-backed dispatcher
(`UnconfinedTestDispatcher(testScheduler)`) and `advanceTimeBy(...)` + `runCurrent()`
— never `advanceUntilIdle()` on a non-terminating effect. See
[`docs/TESTING.md`](docs/TESTING.md).

## License

See the repository for license details.
