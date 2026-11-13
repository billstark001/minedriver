# Visual and shader tests

## Compare a controlled game frame

Set the window size and GUI scale, then record the screenshot's actual pixel dimensions. Pin the language, resources, camera, world state, time/weather, renderer or graphics backend, and frame warm-up when they affect the assertion. Wait for the relevant game state and completed frames before `screenshot.capture`. Keep its PNG and paired UI JSON. The request/completion frame range describes capture timing; use renderer state or an image predicate when the test needs more precise evidence of what was drawn.

Use `screenshot.compare` against an explicitly reviewed baseline with matching dimensions. Define the compared `region` in screenshot pixels and masks relative to that region. Set channel tolerance and maximum changed fraction to suit the expected variance. On a mismatch, retain the actual image, baseline and diff before changing the test.

## Prove shader activation and recovery

Use a Java scenario and, when needed, a fixture-owned `DriverExtension` to expose the renderer's actual active pack, fallback/pipeline state and compile diagnostic. Keep reload and native state access on the required game or render thread. Wait and inspect images from the scenario worker. `ui.snapshot.render.draws` records native GUI geometry and cannot establish final-pass shader output.

1. Capture a controlled shaders-off frame. Load a small, project-owned pack with a known final-pass pixel marker, then require actual activation and check the marker in the game-frame PNG.
2. For a failure check, introduce a deliberate GLSL compilation error. Assert the expected compiler diagnostic, inactive/fallback state and absence of the marker. Apply the same active-pack assertion and require it to reject this state.
3. Reload the valid pack and verify recovery in both state and pixels. Restore the initial shader policy and HUD visibility on success and failure. Report cleanup errors and retain the original assertion failure if both occur.

Record the loader, renderer/mod versions, active graphics backend and device, input pack identity or hashes, pipeline state, screenshots and assertion results. The [Iris fixture](https://github.com/billstark001/minedriver/tree/main/integration/iris) is a worked example using the ordinary scenario, extension and profile APIs.
