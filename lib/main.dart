/// Main entry point for the AWTY Engine example app.
///
/// This is a simple demonstration of the AWTY Engine capabilities.
library awty_engine_main;

import 'package:flutter/material.dart';

void main() {
  runApp(const MyApp());
}

/// Example application demonstrating AWTY Engine integration.
/// This app shows how to use the AWTY Engine for step tracking and goal detection.
class MyApp extends StatelessWidget {
  /// Creates a new instance of the MyApp widget.
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AWTY Engine',
      home: const Scaffold(
        body: Center(child: Text('AWTY Engine - Native step tracking service')),
      ),
    );
  }
}
