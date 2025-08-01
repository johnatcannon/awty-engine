import 'package:flutter/material.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AWTY Engine',
      home: const Scaffold(
        body: Center(
          child: Text('AWTY Engine - Native step tracking service'),
        ),
      ),
    );
  }
} 