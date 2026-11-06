import 'package:astral/core/ui/native_text.dart';
import 'package:astral/core/ui/app_snack_bars.dart';
import 'package:flutter/material.dart';
import 'package:astral/shared/widgets/common/status_bar_actions.dart';
import 'dart:convert';
import 'dart:io';
import 'package:astral/core/models/room.dart';
import 'package:astral/core/room/room_share_codec.dart';
import 'package:astral/core/models/network_config_share.dart';
import 'package:astral/core/app_links/definitions/app_link_definitions.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'native network errors become English without rewriting payload values',
    () {
      expect(
        NativeText.english('AnyhowException(绑定UDP套接字失败: 192.0.2.1:27945)'),
        'AnyhowException(Could not bind UDP socket: 192.0.2.1:27945)',
      );
      expect(
        NativeText.english('[INFO] 实例已成功关闭'),
        '[INFO] Network instance closed',
      );
      expect(
        NativeText.english('新节点已添加。节点ID: 玩家A'),
        'New node added. Node ID: 玩家A',
      );
      expect(NativeText.english('Room selected: 玩家A'), 'Room selected: 玩家A');
      expect(
        NativeText.english('创建服务器: x，启用DHCP: true, 房间密码: secret, 服务器URL: []'),
        'Starting virtual network (room credentials omitted)',
      );
    },
  );
  testWidgets('native failure snackbar is translated at the visible boundary', (
    tester,
  ) async {
    late BuildContext page;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Builder(
            builder: (context) {
              page = context;
              return const Text('Bridge');
            },
          ),
        ),
      ),
    );
    AppSnackBars.error(
      page,
      'Connection failed',
      'AnyhowException(没有运行中的网络实例)',
    );
    await tester.pump();
    expect(
      find.text('AnyhowException(No running network instance)'),
      findsOneWidget,
    );
  });

  testWidgets('language picker offers English and theme labels are English', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: PopupMenuButton<Locale>(
            tooltip: 'Language',
            itemBuilder: (_) => buildLanguageMenuItems(),
          ),
        ),
      ),
    );
    await tester.tap(find.byTooltip('Language'));
    await tester.pumpAndSettle();
    expect(find.text('English'), findsOneWidget);
    expect(find.text('Simplified Chinese'), findsNothing);
    expect(getThemeModeText(ThemeMode.system), 'Follow system');
    expect(getThemeModeText(ThemeMode.dark), 'Dark mode');
  });

  test('existing Astral room links retain wire fields and custom settings', () {
    final payload = {
      'n': 'Member room',
      'r': 'test-room',
      'p': 'test-pass',
      'm': 'test-message-key',
      'e': 1,
      's': ['udp://192.0.2.10:11010'],
      'c': '--latency-first',
      'net': {'dhcp': 1, 'proto': 'udp', 'enc': 1, 'dp2p': 0},
    };
    final code = base64Url
        .encode(gzip.encode(utf8.encode(jsonEncode(payload))))
        .replaceAll('=', '');
    final link = 'astral://room?code=$code';
    expect(AppLinkDefinitions().isValidAstralLink(Uri.parse(link)), isTrue);
    final room = RoomShareCodec.decryptRoom(
      RoomShareCodec.extractShareCode(link)!,
    );
    expect(room, isNotNull);
    expect(room!.roomName, 'test-room');
    expect(room.password, 'test-pass');
    expect(room.encrypted, isTrue);
    expect(room.customParam, '--latency-first');
    final exported = RoomShareCodec.encryptRoom(
      room,
      includeNetworkConfig: true,
    );
    final decoded = jsonDecode(
      utf8.decode(gzip.decode(base64Url.decode(base64Url.normalize(exported)))),
    );
    expect(decoded, payload);
  });

  test('translated sharing text retains importable compatible link', () {
    final room = Room(
      name: 'English room',
      roomName: 'public-room',
      password: '',
      messageKey: '',
      encrypted: false,
    );
    final link = RoomShareCodec.generateShareLink(room);
    expect(link, startsWith('astral://room?code='));
    expect(
      RoomShareCodec.decryptRoom(RoomShareCodec.extractShareCode(link)!)!.name,
      'English room',
    );
    final text = RoomShareCodec.generateShareText(room);
    expect(text, contains('XHYN V Bridge'));
    expect(text, contains(link));
  });

  test('translated network labels preserve protocol settings', () {
    final original = {
      'dhcp': 1,
      'proto': 'udp',
      'enc': 1,
      'lat': 0,
      'dp2p': 0,
      'dudp': 1,
      'dtcp': 0,
      'dsym': 1,
      'comp': 1,
      'kcp': 0,
      'bind': 1,
      'tun': 0,
    };
    final settings = NetworkConfigShare.fromJson(original);
    expect(settings.toJson(), original);
    expect(settings.toReadableSummary().join('\n'), isNot(contains('禁用')));
  });

  test('English resources cover all localization keys and placeholders', () {
    final en =
        jsonDecode(File('assets/translations/en.json').readAsStringSync())
            as Map;
    final zh =
        jsonDecode(File('assets/translations/zh.json').readAsStringSync())
            as Map;
    expect(en.keys.toSet(), zh.keys.toSet());
    final placeholders = RegExp(r'\{[^{}]+\}');
    for (final key in zh.keys) {
      expect(en[key], isNotEmpty, reason: key);
      expect(
        RegExp(r'[\u3400-\u9fff]').hasMatch(en[key] as String),
        isFalse,
        reason: key,
      );
      expect(
        placeholders.allMatches(en[key] as String).map((m) => m[0]).toSet(),
        placeholders.allMatches(zh[key] as String).map((m) => m[0]).toSet(),
        reason: key,
      );
    }
  });
}
