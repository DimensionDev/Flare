//
// ProgressBarView.swift
// TwidereX
//
// Created by Cirno MainasuK on 2020-11-24.
// Copyright © 2020 Twidere. All rights reserved.
//

import Combine
import UIKit

final class ProgressBarView: UIView {
    var disposeBag = Set<AnyCancellable>()

    var borderWidth: CGFloat = 4
    var margin: CGFloat = 10

    private lazy var backBarLayer: CAShapeLayer = {
        let shapeLayer = CAShapeLayer()
        shapeLayer.lineCap = .round
        shapeLayer.fillColor = UIColor.clear.cgColor
        shapeLayer.strokeColor = tintColor.cgColor
        return shapeLayer
    }()

    private lazy var frontBarLayer: CAShapeLayer = {
        let shapeLayer = CAShapeLayer()
        shapeLayer.lineCap = .round
        shapeLayer.fillColor = tintColor.cgColor
        shapeLayer.strokeColor = UIColor.clear.cgColor
        return shapeLayer
    }()

    let progressMaskLayer: CAShapeLayer = {
        let shapeLayer = CAShapeLayer()
        shapeLayer.fillColor = UIColor.red.cgColor
        return shapeLayer
    }()

    let progress = CurrentValueSubject<CGFloat, Never>(0.0)

    override init(frame: CGRect) {
        super.init(frame: frame)
        _init()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        _init()
    }
}

extension ProgressBarView {
    private func _init() {
        updateLayerPath()

        layer.addSublayer(backBarLayer)
        layer.addSublayer(frontBarLayer)

        progress
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self else { return }
                updateLayerPath()
            }
            .store(in: &disposeBag)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        updateLayerPath()
    }
}

extension ProgressBarView {
    private func updateLayerPath() {
        guard bounds != .zero else { return }

        backBarLayer.frame = bounds
        backBarLayer.strokeColor = tintColor.cgColor
        backBarLayer.lineWidth = borderWidth

        frontBarLayer.frame = bounds
        frontBarLayer.fillColor = tintColor.cgColor

        progressMaskLayer.frame = bounds

        backBarLayer.path = {
            let path = UIBezierPath(roundedRect: bounds.insetBy(dx: margin, dy: margin), cornerRadius: 0.5 * bounds.height)
            return path.cgPath
        }()

        frontBarLayer.path = {
            let path = UIBezierPath(roundedRect: bounds.insetBy(dx: margin + borderWidth, dy: margin + borderWidth), cornerRadius: 0.5 * bounds.height)
            return path.cgPath
        }()

        progressMaskLayer.path = {
            var rect = bounds.insetBy(dx: margin + borderWidth, dy: margin + borderWidth)
            let newWidth = progress.value * rect.width
            let widthChanged = rect.width - newWidth
            rect.size.width = newWidth
            switch UIApplication.shared.userInterfaceLayoutDirection {
            case .rightToLeft:
                rect.origin.x += widthChanged
            default:
                break
            }
            let path = UIBezierPath(rect: rect)
            return path.cgPath
        }()
        frontBarLayer.mask = progressMaskLayer
    }
}
