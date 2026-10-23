package io.github.denberg28.telerc

/** Presentation only: fixed course seeds, collision geometry and record version stay unchanged. */
internal enum class MazeEnvironment(val label:String,val floor:Int,val wall:Int,val detail:Int) {
    CITY("City",0xff263444.toInt(),0xffbfcedd.toInt(),0xff3b4d60.toInt()),
    FOREST("Forest",0xff193a32.toInt(),0xffb8d7a0.toInt(),0xff2e5540.toInt()),
    DESERT("Desert",0xff654c36.toInt(),0xffffd6a0.toInt(),0xff866547.toInt()),
    SPACE("Space",0xff151e39.toInt(),0xffc2b9f0.toInt(),0xff364365.toInt());
    companion object {
        fun forLevel(level:Int)=entries[(level.coerceIn(1,MazeLevels.COUNT)-1)/5]
    }
}
