package ro.cosminmihu.ktor.monitor.ui.main

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEvent.Companion.EDGE_LEFT
import ro.cosminmihu.ktor.monitor.ui.detail.DetailRoute
import ro.cosminmihu.ktor.monitor.ui.list.ListRoute
import ro.cosminmihu.ktor.monitor.ui.nav.Nav
import ro.cosminmihu.ktor.monitor.ui.nav.backStackConfig
import ro.cosminmihu.ktor.monitor.ui.nav.rememberListDetailSceneStrategy

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun MainContent(modifier: Modifier = Modifier) {
  val backStack = rememberNavBackStack(backStackConfig, Nav.List)
  var selectionCount by remember { mutableStateOf<Int?>(null) }
  val listDetailStrategy = rememberListDetailSceneStrategy()

  Surface(modifier = modifier) {
    NavDisplay(
      modifier = Modifier.fillMaxSize(),
      backStack = backStack,
      onBack = { backStack.removeLastOrNull() },
      sceneStrategies = listOf(listDetailStrategy),
      entryProvider = entryProvider {
        entry<Nav.List>(
          metadata = ListDetailSceneStrategy.listPane(
            detailPlaceholder = {
              DetailRoute(
                id = null,
                selectionCount = selectionCount,
                onBack = {},
                modifier = Modifier.fillMaxSize()
              )
            }
          )
        ) {
          ListRoute(
            onSelectionCountChange = { selectionCount = it },
            onClick = { id ->
              backStack.removeAll { it is Nav.Detail }
              backStack.add(Nav.Detail(id))
            }
          )
        }
        entry<Nav.Detail>(
          metadata = ListDetailSceneStrategy.detailPane()
        ) { destination ->
          DetailRoute(
            id = destination.id,
            selectionCount = selectionCount,
            onBack = { backStack.removeLastOrNull() }
          )
        }
      },
      predictivePopTransitionSpec = ktorMonitorPredictivePopTransitionSpec(),
    )
  }
}

private fun <T : Any> ktorMonitorPredictivePopTransitionSpec():
    AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform =
  { edge ->
    val towards = if (edge == EDGE_LEFT) {
      AnimatedContentTransitionScope.SlideDirection.Right
    } else {
      AnimatedContentTransitionScope.SlideDirection.Left
    }

    ContentTransform(
      targetContentEnter = slideIntoContainer(
        towards = towards,
        initialOffset = { 300 },
        animationSpec = tween(
          durationMillis = 300,
          easing = FastOutSlowInEasing
        ),
      ) + fadeIn(
        animationSpec = tween(durationMillis = 300)
      ),
      initialContentExit = slideOutOfContainer(
        towards = towards,
        targetOffset = { 300 },
        animationSpec = tween(
          durationMillis = 300,
          easing = FastOutSlowInEasing
        ),
      ) + fadeOut(
        animationSpec = tween(durationMillis = 300)
      ),
    )
  }
