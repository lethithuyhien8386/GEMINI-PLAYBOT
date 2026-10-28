# 🐸 Frogs Kitchen Auto AI 100% (`com.outloud.idlefrog`)

Dự án Android chuyển đổi từ ứng dụng `AdsCloser_v5` sang **AI Auto-Player 100%** dành riêng cho tựa game **Frogs Kitchen (Frog Kitchen Tycoon: Idle Venture)** trên Google Play (`com.outloud.idlefrog`).

## ✨ Tính năng chính
1. **Tự động chơi 100% từ đầu bằng Gemini 2.0 Flash Vision**:
   - **Tự vào game & làm Tutorial**: Tự nhận diện nút *Play, Start, Continue, Claim* và mũi tên/bàn tay hướng dẫn khi mới bắt đầu.
   - **Tự mở khóa & mở thùng trạm nấu ăn (Unbox Stations)**: Tự mua bàn chế biến mới và chạm mở thùng gỗ để gọi đầu bếp ếch.
   - **Tự leo Level trạm nấu ăn cực nhanh (`HOLD` Gesture)**: Hỗ trợ thao tác **nhấn giữ (Long-Press 1250ms)** vào nút nâng cấp trạm để tăng hàng chục level liên tục.
   - **Tự mua Upgrades & thuê Đầu bếp ếch (Frog Chefs)**: Tự mở menu nâng cấp góc dưới phải, nâng tốc độ nấu, tăng giá món ăn, thêm khách hàng và thuê thêm ếch.
   - **Tự động Qua màn / Sang nhà hàng & thế giới mới**: Khi các trạm đạt tối đa, AI tự bấm biểu tượng nâng cấp nhà hàng / qua màn mới.
   - **Tự đóng quảng cáo & nhận thưởng Offline**: Vẫn giữ nguyên khả năng đóng quảng cáo xen kẽ và nhận thưởng túi đồ/hộp quà miễn phí.
2. **Tự động mở game Frogs Kitchen khi bấm nút Play trên bong bóng nổi**.

## 🚀 Hướng dẫn Build ra file `.apk` tự động trên GitHub
1. Giải nén file `FrogsKitchenAutoAI_Source.zip` này và đẩy (push) toàn bộ mã nguồn lên một Repository trên GitHub (nhánh `main` hoặc `master`).
2. Vào tab **Actions** trên GitHub Repository của bạn.
3. Workflow **Build FrogsKitchenAuto APK** sẽ tự động chạy (hoặc bạn có thể bấm **Run workflow**).
4. Sau khoảng 1-2 phút khi chạy xong, kéo xuống mục **Artifacts** và tải file **`FrogsKitchenAuto-debug-apk`** về giải nén để lấy `app-debug.apk`.
