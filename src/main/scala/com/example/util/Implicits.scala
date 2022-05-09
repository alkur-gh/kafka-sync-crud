package com.example.util

import com.example.kafkapacket.PacketTypes
import org.json4s.ext.EnumNameSerializer
import org.json4s.{DefaultFormats, Formats}

object Implicits {
  implicit val formats: Formats = DefaultFormats + new EnumNameSerializer(PacketTypes)
}
