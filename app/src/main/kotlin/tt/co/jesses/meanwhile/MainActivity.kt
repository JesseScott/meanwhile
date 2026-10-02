package tt.co.jesses.meanwhile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import tt.co.jesses.meanwhile.ui.MeanwhileApp
import tt.co.jesses.meanwhile.ui.theme.MeanwhileTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeanwhileTheme {
                Surface {
                    MeanwhileApp(viewModel)
                }
            }
        }
    }
}
